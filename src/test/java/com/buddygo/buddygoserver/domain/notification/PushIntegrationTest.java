package com.buddygo.buddygoserver.domain.notification;

import com.buddygo.buddygoserver.domain.gathering.entity.Gathering;
import com.buddygo.buddygoserver.domain.gathering.entity.Place;
import com.buddygo.buddygoserver.domain.gathering.enums.GenderRule;
import com.buddygo.buddygoserver.domain.gathering.repository.GatheringRepository;
import com.buddygo.buddygoserver.domain.gathering.repository.PlaceRepository;
import com.buddygo.buddygoserver.domain.notification.enums.NotificationType;
import com.buddygo.buddygoserver.domain.notification.push.PushClient;
import com.buddygo.buddygoserver.domain.notification.push.PushMessage;
import com.buddygo.buddygoserver.domain.notification.service.NotificationSender;
import com.buddygo.buddygoserver.domain.notification.support.NotificationTarget;
import com.buddygo.buddygoserver.domain.user.entity.User;
import com.buddygo.buddygoserver.domain.user.enums.Gender;
import com.buddygo.buddygoserver.domain.user.repository.DeviceTokenRepository;
import com.buddygo.buddygoserver.domain.user.repository.UserRepository;
import com.buddygo.buddygoserver.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

/**
 * 커밋 후 비동기로 푸시한다. 실제로 커밋해야 하므로 롤백하지 않고, 테스트가 만든 행만 지운다.
 * - alice: 기기 2대(phone, tablet), 알림 켬
 * - bob: 기기 1대, 전체 알림 끔
 * - carol: 기기 없음
 */
class PushIntegrationTest extends IntegrationTest {

    static final Instant NOW = Instant.parse("2026-11-10T00:00:00Z");

    @Override
    protected Instant fixedNow() {
        return NOW;
    }

    @Autowired NotificationSender notificationSender;
    @Autowired PushClient pushClient;
    @Autowired UserRepository userRepository;
    @Autowired DeviceTokenRepository deviceTokenRepository;
    @Autowired PlaceRepository placeRepository;
    @Autowired GatheringRepository gatheringRepository;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired JdbcTemplate jdbc;

    TransactionTemplate tx;
    String suffix;
    Long aliceId, bobId, carolId, gatheringId, placeId;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(transactionManager);
        suffix = UUID.randomUUID().toString().substring(0, 8);
        tx.executeWithoutResult(status -> {
            aliceId = user("a").getId();
            User bob = user("b");
            bob.changePushEnabled(false);
            bobId = bob.getId();
            carolId = user("c").getId();
            deviceTokenRepository.upsert(aliceId, token("phone"), NOW);
            deviceTokenRepository.upsert(aliceId, token("tablet"), NOW);
            deviceTokenRepository.upsert(bobId, token("bob"), NOW);
            Place place = placeRepository.save(Place.pinned("푸시 테스트", null, "서울 강남구 역삼동 1", "1168010100",
                    new GeometryFactory(new PrecisionModel(), 4326).createPoint(new Coordinate(127, 37.5))));
            placeId = place.getId();
            gatheringId = gatheringRepository.save(Gathering.open(userRepository.getReferenceById(aliceId), place,
                    "저녁 가슴", null, null, NOW.plusSeconds(10800), Duration.ofHours(1), (short) 3, GenderRule.ANY))
                    .getId();
        });
    }

    @AfterEach
    void cleanup() {
        tx.executeWithoutResult(status -> {
            jdbc.update("delete from notifications where receiver_id in (?, ?, ?)", aliceId, bobId, carolId);
            jdbc.update("delete from device_tokens where user_id in (?, ?, ?)", aliceId, bobId, carolId);
            jdbc.update("delete from gatherings where id = ?", gatheringId);
            jdbc.update("delete from places where id = ?", placeId);
            jdbc.update("delete from users where id in (?, ?, ?)", aliceId, bobId, carolId);
        });
    }

    @Test
    void 커밋되면_알림을_켠_사람의_모든_기기로_보내고_제목은_앱_이름이다() {
        tx.executeWithoutResult(status -> notificationSender.send(NotificationType.APPLICATION_ACCEPTED,
                List.of(aliceId, bobId, carolId), null, NotificationTarget.gathering(gatheringId),
                "'저녁 가슴' 모임 참여가 확정됐어요."));

        List<PushMessage> sent = captureSent();
        assertThat(sent).extracting(PushMessage::token).containsExactlyInAnyOrder(token("phone"), token("tablet"));
        Long notificationId = jdbc.queryForObject(
                "select id from notifications where receiver_id = ?", Long.class, aliceId);
        assertThat(sent).allSatisfy(m -> {
            assertThat(m.title()).isEqualTo("버디고");
            assertThat(m.body()).isEqualTo("'저녁 가슴' 모임 참여가 확정됐어요.");
            assertThat(m.data()).containsOnly(
                    org.assertj.core.api.Assertions.entry("type", "APPLICATION_ACCEPTED"),
                    org.assertj.core.api.Assertions.entry("notificationId", String.valueOf(notificationId)),
                    org.assertj.core.api.Assertions.entry("gatheringId", String.valueOf(gatheringId)));
        });
        // 알림을 끈 bob 도 앱 알림 목록에는 남는다
        assertThat(jdbc.queryForObject("select count(*) from notifications where receiver_id = ?", Long.class, bobId))
                .isEqualTo(1);
    }

    @Test
    void 롤백되면_보내지_않는다() {
        tx.executeWithoutResult(status -> {
            notificationSender.send(NotificationType.APPLICATION_ACCEPTED, List.of(aliceId), null,
                    NotificationTarget.gathering(gatheringId), "확정");
            status.setRollbackOnly();
        });

        verify(pushClient, after(500).never()).send(anyList());
    }

    @Test
    void FCM이_없다고_한_토큰은_지우고_나머지는_남긴다() {
        doReturn(Set.of(token("phone"))).when(pushClient).send(anyList());

        tx.executeWithoutResult(status -> notificationSender.send(NotificationType.APPLICATION_ACCEPTED,
                List.of(aliceId), null, NotificationTarget.gathering(gatheringId), "확정"));

        await().atMost(Duration.ofSeconds(3)).untilAsserted(() ->
                assertThat(deviceTokenRepository.findAllByUserId(aliceId))
                        .extracting(t -> t.getFcmToken()).containsExactly(token("tablet")));
    }

    @Test
    void 읽은_뒤_다시_등록된_토큰은_FCM이_없다고_해도_지우지_않는다() {
        // 전송하는 사이에 같은 토큰이 다시 등록된 상황: updated_at 이 푸시가 토큰을 읽은 시각보다 늦다
        doReturn(Set.of(token("phone"))).when(pushClient).send(anyList());
        tx.executeWithoutResult(status ->
                deviceTokenRepository.upsert(aliceId, token("phone"), NOW.plusSeconds(60)));

        tx.executeWithoutResult(status -> notificationSender.send(NotificationType.APPLICATION_ACCEPTED,
                List.of(aliceId), null, NotificationTarget.gathering(gatheringId), "확정"));

        verify(pushClient, timeout(3000)).send(anyList());
        await().during(Duration.ofMillis(300)).atMost(Duration.ofSeconds(1)).untilAsserted(() ->
                assertThat(deviceTokenRepository.findAllByUserId(aliceId)).hasSize(2));
    }

    @Test
    void 모임당_한_번뿐인_알림은_다시_돌아도_새로_들어간_것만_보낸다() {
        tx.executeWithoutResult(status -> notificationSender.sendOncePerGathering(NotificationType.CHECK_IN_OPENED,
                List.of(aliceId), gatheringId, "체크인 열림"));
        verify(pushClient, timeout(3000)).send(anyList());

        tx.executeWithoutResult(status -> notificationSender.sendOncePerGathering(NotificationType.CHECK_IN_OPENED,
                List.of(aliceId), gatheringId, "체크인 열림"));
        verify(pushClient, after(500).times(1)).send(anyList());
    }

    @Test
    void 전송이_실패해도_다음_푸시는_보낸다() {
        doThrow(new IllegalStateException("FCM 장애")).doReturn(Set.of()).when(pushClient).send(anyList());

        for (int i = 0; i < 2; i++) {
            tx.executeWithoutResult(status -> notificationSender.send(NotificationType.APPLICATION_ACCEPTED,
                    List.of(aliceId), null, NotificationTarget.gathering(gatheringId), "확정"));
        }

        verify(pushClient, timeout(3000).times(2)).send(any());
    }

    @SuppressWarnings("unchecked")
    private List<PushMessage> captureSent() {
        ArgumentCaptor<List<PushMessage>> captor = ArgumentCaptor.forClass(List.class);
        verify(pushClient, timeout(3000)).send(captor.capture());
        return captor.getValue();
    }

    private String token(String device) {
        return device + "-" + suffix;
    }

    private User user(String key) {
        return userRepository.save(User.signUpWithEmail(key + suffix + "@example.com", "hash", "이름",
                key + suffix, Gender.MALE));
    }
}
