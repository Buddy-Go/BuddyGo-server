package com.buddygo.buddygoserver.domain.gathering;

import com.buddygo.buddygoserver.domain.gathering.entity.Gathering;
import com.buddygo.buddygoserver.domain.gathering.exception.GatheringErrorType;
import com.buddygo.buddygoserver.domain.gathering.entity.Place;
import com.buddygo.buddygoserver.domain.gathering.enums.GenderRule;
import com.buddygo.buddygoserver.domain.gathering.repository.GatheringRepository;
import com.buddygo.buddygoserver.domain.gathering.repository.PlaceRepository;
import com.buddygo.buddygoserver.domain.gathering.service.GatheringLikeCommandService;
import com.buddygo.buddygoserver.domain.user.entity.User;
import com.buddygo.buddygoserver.domain.user.enums.Gender;
import com.buddygo.buddygoserver.domain.user.repository.UserRepository;
import com.buddygo.buddygoserver.global.exception.CustomException;
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

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 실제로 커밋하며 동시 요청을 낸다. 테스트가 만든 행만 정리한다. */
class GatheringLikeConcurrencyTest extends IntegrationTest {

    @Autowired GatheringLikeCommandService likeService;
    @Autowired UserRepository userRepository;
    @Autowired PlaceRepository placeRepository;
    @Autowired GatheringRepository gatheringRepository;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired JdbcTemplate jdbc;

    Long hostId, memberId, placeId, gatheringId;

    @BeforeEach
    void setUp() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            String suffix = UUID.randomUUID().toString().substring(0, 8);
            User host = userRepository.save(User.signUpWithEmail("lh" + suffix + "@example.com", "hash", "호스트", "lh" + suffix, Gender.MALE));
            User member = userRepository.save(User.signUpWithEmail("lm" + suffix + "@example.com", "hash", "멤버", "lm" + suffix, Gender.MALE));
            Place place = placeRepository.save(Place.pinned("찜 동시성", null, "서울 강남구 역삼동 1", "1168010100",
                    new GeometryFactory(new PrecisionModel(), 4326).createPoint(new Coordinate(127, 37.5))));
            Gathering gathering = gatheringRepository.save(Gathering.open(host, place, "찜 테스트", null, null,
                    Instant.now().plus(Duration.ofDays(1)), Duration.ofHours(1), (short) 3, GenderRule.ANY));
            hostId = host.getId();
            memberId = member.getId();
            placeId = place.getId();
            gatheringId = gathering.getId();
        });
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from gathering_likes where gathering_id = ?", gatheringId);
        jdbc.update("delete from gatherings where id = ?", gatheringId);
        jdbc.update("delete from places where id = ?", placeId);
        jdbc.update("delete from users where id in (?, ?)", hostId, memberId);
    }

    @Test
    void 같은_찜을_동시에_보내도_둘다_성공하고_한_건만_남는다() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> {
                ready.countDown();
                start.await(10, TimeUnit.SECONDS);
                likeService.like(memberId, gatheringId);
                return null;
            });
            var second = executor.submit(() -> {
                ready.countDown();
                start.await(10, TimeUnit.SECONDS);
                likeService.like(memberId, gatheringId);
                return null;
            });
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            first.get(20, TimeUnit.SECONDS);
            second.get(20, TimeUnit.SECONDS);
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
        assertThat(jdbc.queryForObject("select count(*) from gathering_likes where gathering_id = ?",
                Long.class, gatheringId)).isEqualTo(1);
    }

    @Test
    void 모임_행이_호스트_작업으로_잠겨_있어도_찜은_기다리지_않는다() throws Exception {
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        var executor = Executors.newSingleThreadExecutor();
        try {
            var holder = executor.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                gatheringRepository.findByIdForUpdate(gatheringId).orElseThrow();
                locked.countDown();
                try {
                    release.await(20, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
            var like = Executors.newSingleThreadExecutor().submit(() -> likeService.like(memberId, gatheringId));
            like.get(5, TimeUnit.SECONDS);
            release.countDown();
            holder.get(20, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
        assertThat(jdbc.queryForObject("select count(*) from gathering_likes where gathering_id = ?",
                Long.class, gatheringId)).isEqualTo(1);
    }

    @Test
    void 삭제_중인_모임에_찜하면_삭제를_기다렸다가_GA001() throws Exception {
        CountDownLatch deleted = new CountDownLatch(1);
        CountDownLatch commit = new CountDownLatch(1);
        var deleter = Executors.newSingleThreadExecutor();
        var liker = Executors.newSingleThreadExecutor();
        try {
            var deletion = deleter.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                jdbc.update("delete from gatherings where id = ?", gatheringId);
                deleted.countDown();
                try {
                    commit.await(20, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
            assertThat(deleted.await(10, TimeUnit.SECONDS)).isTrue();
            var like = liker.submit(() -> likeService.like(memberId, gatheringId));
            // 삭제가 커밋되기 전에는 찜이 끝나지 않는다(FOR KEY SHARE 가 DELETE 를 기다린다).
            assertThatThrownBy(() -> like.get(1, TimeUnit.SECONDS)).isInstanceOf(TimeoutException.class);
            commit.countDown();
            deletion.get(20, TimeUnit.SECONDS);
            assertThatThrownBy(() -> like.get(20, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class)
                    .cause().isInstanceOf(CustomException.class)
                    .extracting(e -> ((CustomException) e).getErrorType())
                    .isEqualTo(GatheringErrorType.NOT_FOUND);
        } finally {
            commit.countDown();
            deleter.shutdownNow();
            liker.shutdownNow();
        }
    }
}
