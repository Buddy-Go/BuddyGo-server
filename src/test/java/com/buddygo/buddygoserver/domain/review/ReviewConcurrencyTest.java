package com.buddygo.buddygoserver.domain.review;

import com.buddygo.buddygoserver.domain.application.entity.Participation;
import com.buddygo.buddygoserver.domain.application.enums.ParticipationStatus;
import com.buddygo.buddygoserver.domain.application.repository.ParticipationRepository;
import com.buddygo.buddygoserver.domain.checkin.entity.CheckIn;
import com.buddygo.buddygoserver.domain.checkin.enums.Attendance;
import com.buddygo.buddygoserver.domain.checkin.repository.CheckInRepository;
import com.buddygo.buddygoserver.domain.gathering.entity.Gathering;
import com.buddygo.buddygoserver.domain.gathering.entity.Place;
import com.buddygo.buddygoserver.domain.gathering.enums.GatheringStatus;
import com.buddygo.buddygoserver.domain.gathering.enums.GenderRule;
import com.buddygo.buddygoserver.domain.gathering.repository.GatheringRepository;
import com.buddygo.buddygoserver.domain.gathering.repository.PlaceRepository;
import com.buddygo.buddygoserver.domain.review.dto.request.ReviewSubmitRequest;
import com.buddygo.buddygoserver.domain.review.enums.ReviewRating;
import com.buddygo.buddygoserver.domain.review.enums.ReviewTagType;
import com.buddygo.buddygoserver.domain.review.service.ReviewCommandService;
import com.buddygo.buddygoserver.domain.user.entity.User;
import com.buddygo.buddygoserver.domain.user.enums.Gender;
import com.buddygo.buddygoserver.domain.user.repository.UserRepository;
import com.buddygo.buddygoserver.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** 실제로 커밋하며 동시 요청을 낸다. 테스트가 만든 행만 정리한다. */
class ReviewConcurrencyTest extends IntegrationTest {

    @Autowired ReviewCommandService reviewCommandService;
    @Autowired UserRepository userRepository;
    @Autowired PlaceRepository placeRepository;
    @Autowired GatheringRepository gatheringRepository;
    @Autowired ParticipationRepository participationRepository;
    @Autowired CheckInRepository checkInRepository;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired JdbcTemplate jdbc;

    Long hostId, aId, bId, placeId, gatheringId;

    @BeforeEach
    void setUp() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            String suffix = UUID.randomUUID().toString().substring(0, 8);
            User host = userRepository.save(User.signUpWithEmail("rh" + suffix + "@example.com", "hash", "호스트", "rh" + suffix, Gender.MALE));
            User a = userRepository.save(User.signUpWithEmail("ra" + suffix + "@example.com", "hash", "에이", "ra" + suffix, Gender.MALE));
            User b = userRepository.save(User.signUpWithEmail("rb" + suffix + "@example.com", "hash", "비", "rb" + suffix, Gender.MALE));
            Place place = placeRepository.save(Place.pinned("평가 동시성", null, "서울 강남구 역삼동 1", "1168010100",
                    new GeometryFactory(new PrecisionModel(), 4326).createPoint(new Coordinate(127, 37.5))));
            Instant start = Instant.now().minus(Duration.ofHours(3));
            Gathering gathering = gatheringRepository.save(Gathering.builder()
                    .host(host).place(place).title("평가 테스트").startAt(start).endAt(start.plus(Duration.ofHours(1)))
                    .capacity((short) 3).genderRule(GenderRule.ANY).status(GatheringStatus.FINISHED).build());
            Participation hostParticipation = participationRepository.save(Participation.hostOf(gathering, start.minus(Duration.ofDays(1))));
            checkIn(hostParticipation);
            for (User member : List.of(a, b)) {
                checkIn(participationRepository.save(Participation.builder().gathering(gathering).user(member)
                        .status(ParticipationStatus.CONFIRMED).appliedAt(start.minus(Duration.ofDays(1)))
                        .decidedAt(start.minus(Duration.ofDays(1))).build()));
            }
            hostId = host.getId();
            aId = a.getId();
            bId = b.getId();
            placeId = place.getId();
            gatheringId = gathering.getId();
        });
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from manner_temperature_logs where review_id in (select id from reviews where gathering_id = ?)", gatheringId);
        jdbc.update("delete from review_tags where review_id in (select id from reviews where gathering_id = ?)", gatheringId);
        jdbc.update("delete from reviews where gathering_id = ?", gatheringId);
        jdbc.update("delete from check_ins where participation_id in (select id from participations where gathering_id = ?)", gatheringId);
        jdbc.update("delete from gatherings where id = ?", gatheringId);
        jdbc.update("delete from places where id = ?", placeId);
        jdbc.update("delete from users where id in (?, ?, ?)", hostId, aId, bId);
    }

    @Test
    void 두_사람이_같은_상대를_동시에_평가해도_두_변화가_모두_반영된다() throws Exception {
        runConcurrently(
                () -> reviewCommandService.submit(aId, gatheringId, good(hostId)),
                () -> reviewCommandService.submit(bId, gatheringId, good(hostId)));

        // 둘 다 +0.50 이 반영돼 37.50 이다. 잠그지 않으면 둘 다 36.50 을 읽고 37.00 을 써서 하나가 사라진다.
        BigDecimal temperature = jdbc.queryForObject("select manner_temperature from users where id = ?", BigDecimal.class, hostId);
        BigDecimal logged = jdbc.queryForObject("select sum(delta) from manner_temperature_logs where user_id = ?", BigDecimal.class, hostId);
        assertThat(jdbc.queryForObject("select count(*) from manner_temperature_logs where user_id = ?", Long.class, hostId)).isEqualTo(2);
        assertThat(new BigDecimal("36.50").add(logged)).isEqualByComparingTo(temperature);
        assertThat(temperature).isEqualByComparingTo("37.50");
    }

    @Test
    void 서로를_동시에_평가해도_교착_없이_둘_다_반영된다() throws Exception {
        runConcurrently(
                () -> reviewCommandService.submit(aId, gatheringId, good(bId)),
                () -> reviewCommandService.submit(bId, gatheringId, good(aId)));

        assertThat(jdbc.queryForObject("select count(*) from reviews where gathering_id = ?", Long.class, gatheringId)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select manner_temperature from users where id = ?", BigDecimal.class, aId))
                .isEqualByComparingTo("37.00");
        assertThat(jdbc.queryForObject("select manner_temperature from users where id = ?", BigDecimal.class, bId))
                .isEqualByComparingTo("37.00");
    }

    /** 좋음 + 긍정·부정 태그 하나씩: 태그 점수가 상쇄돼 한 건에 +0.50 */
    private static ReviewSubmitRequest good(Long targetId) {
        return new ReviewSubmitRequest(targetId, ReviewRating.GOOD, Set.of(ReviewTagType.KIND, ReviewTagType.ON_PHONE), null);
    }

    private void checkIn(Participation participation) {
        checkInRepository.save(CheckIn.builder().participation(participation).attendance(Attendance.ON_TIME).build());
    }

    private void runConcurrently(Runnable first, Runnable second) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (Runnable task : List.of(first, second)) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await(10, TimeUnit.SECONDS);
                    task.run();
                    return null;
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (Future<?> future : futures) {
                future.get(20, TimeUnit.SECONDS);
            }
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }
}
