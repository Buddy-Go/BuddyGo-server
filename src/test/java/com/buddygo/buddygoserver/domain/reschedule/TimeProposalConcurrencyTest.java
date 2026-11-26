package com.buddygo.buddygoserver.domain.reschedule;

import com.buddygo.buddygoserver.domain.application.entity.Participation;
import com.buddygo.buddygoserver.domain.application.enums.ParticipationStatus;
import com.buddygo.buddygoserver.domain.application.repository.ParticipationRepository;
import com.buddygo.buddygoserver.domain.gathering.entity.Gathering;
import com.buddygo.buddygoserver.domain.gathering.entity.Place;
import com.buddygo.buddygoserver.domain.gathering.enums.GatheringStatus;
import com.buddygo.buddygoserver.domain.gathering.enums.GenderRule;
import com.buddygo.buddygoserver.domain.gathering.repository.GatheringRepository;
import com.buddygo.buddygoserver.domain.gathering.repository.PlaceRepository;
import com.buddygo.buddygoserver.domain.reschedule.dto.request.TimeProposalCreateRequest;
import com.buddygo.buddygoserver.domain.reschedule.service.TimeProposalCommandService;
import com.buddygo.buddygoserver.domain.user.entity.User;
import com.buddygo.buddygoserver.domain.user.enums.Gender;
import com.buddygo.buddygoserver.domain.user.repository.UserRepository;
import com.buddygo.buddygoserver.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** 실제로 커밋하며 동시 요청을 낸다. 테스트가 만든 행만 정리한다. */
class TimeProposalConcurrencyTest extends IntegrationTest {

    static final Instant NOW = Instant.parse("2026-11-10T00:00:00Z");
    static final Instant START = Instant.parse("2026-11-10T03:00:00Z");
    static final Instant TO = Instant.parse("2026-11-10T03:30:00Z");

    @Override
    protected Instant fixedNow() {
        return NOW;
    }

    @Autowired TimeProposalCommandService commandService;
    @Autowired UserRepository userRepository;
    @Autowired PlaceRepository placeRepository;
    @Autowired GatheringRepository gatheringRepository;
    @Autowired ParticipationRepository participationRepository;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired JdbcTemplate jdbc;

    Long hostId, aId, bId, placeId, gatheringId;

    @BeforeEach
    void setUp() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            String suffix = UUID.randomUUID().toString().substring(0, 8);
            User host = userRepository.save(User.signUpWithEmail("th" + suffix + "@example.com", "hash", "호스트", "th" + suffix, Gender.MALE));
            User a = userRepository.save(User.signUpWithEmail("ta" + suffix + "@example.com", "hash", "에이", "ta" + suffix, Gender.MALE));
            User b = userRepository.save(User.signUpWithEmail("tb" + suffix + "@example.com", "hash", "비", "tb" + suffix, Gender.MALE));
            Place place = placeRepository.save(Place.pinned("제안 동시성", null, "서울 강남구 역삼동 1", "1168010100",
                    new GeometryFactory(new PrecisionModel(), 4326).createPoint(new Coordinate(127, 37.5))));
            Gathering gathering = gatheringRepository.save(Gathering.builder()
                    .host(host).place(place).title("제안 테스트").startAt(START).endAt(START.plus(Duration.ofHours(1)))
                    .capacity((short) 3).genderRule(GenderRule.ANY).status(GatheringStatus.CLOSED).build());
            participationRepository.save(Participation.hostOf(gathering, NOW.minus(Duration.ofDays(1))));
            for (User member : List.of(a, b)) {
                participationRepository.save(Participation.builder().gathering(gathering).user(member)
                        .status(ParticipationStatus.CONFIRMED).appliedAt(NOW.minus(Duration.ofDays(1)))
                        .decidedAt(NOW.minus(Duration.ofDays(1))).build());
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
        jdbc.update("delete from notifications where receiver_id in (?, ?, ?)", hostId, aId, bId);
        jdbc.update("delete from gatherings where id = ?", gatheringId);
        jdbc.update("delete from places where id = ?", placeId);
        jdbc.update("delete from users where id in (?, ?, ?)", hostId, aId, bId);
    }

    @RepeatedTest(5)
    void 마지막_두_명이_동시에_수락해도_한_번만_통과하고_시각이_바뀐다() throws Exception {
        Long proposalId = commandService.propose(aId, gatheringId, new TimeProposalCreateRequest(TO)).proposalId();

        runConcurrently(
                () -> commandService.accept(hostId, gatheringId, proposalId),
                () -> commandService.accept(bId, gatheringId, proposalId));

        // 모임 행을 잠그지 않으면 둘 다 상대가 아직 PENDING 인 투표를 읽어 아무도 통과시키지 않는다
        assertThat(jdbc.queryForObject("select status from gathering_time_proposals where id = ?", String.class, proposalId))
                .isEqualTo("APPROVED");
        assertThat(jdbc.queryForObject("select start_at from gatherings where id = ?", Timestamp.class, gatheringId)
                .toInstant()).isEqualTo(TO);
        // 마지막으로 수락한 한 사람을 뺀 두 명에게 한 번씩
        assertThat(jdbc.queryForObject(
                "select count(*) from notifications where gathering_id = ? and type = 'TIME_CHANGE_APPROVED'",
                Long.class, gatheringId)).isEqualTo(2);
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
