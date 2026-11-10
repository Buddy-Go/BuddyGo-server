package com.buddygo.buddygoserver.domain.user.repository;

import com.buddygo.buddygoserver.domain.user.entity.DeviceToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface DeviceTokenRepository extends JpaRepository<DeviceToken, Long> {

    List<DeviceToken> findAllByUserId(Long userId);

    /**
     * 토큰을 이 사용자에게 등록한다. 같은 기기에서 다른 계정으로 로그인해 이미 있는 토큰이 오면
     * 소유자를 바꾼다. 조회 후 INSERT 가 아니라 한 문장이라 동시 등록에도 유니크 위반이 나지 않는다.
     * 네이티브 쿼리라 Auditing 이 돌지 않으므로 updated_at 을 직접 넣는다(Clock 의 현재 시각).
     */
    @Modifying(clearAutomatically = true)
    @Query(value = """
            INSERT INTO device_tokens (user_id, fcm_token, updated_at)
            VALUES (:userId, :fcmToken, :updatedAt)
            ON CONFLICT (fcm_token)
            DO UPDATE SET user_id = EXCLUDED.user_id, updated_at = EXCLUDED.updated_at
            """, nativeQuery = true)
    void upsert(Long userId, String fcmToken, Instant updatedAt);

    /** 내 토큰만 지운다(로그아웃). 다른 사람에게 옮겨간 토큰은 지우지 않는다. */
    @Modifying
    @Query("delete from DeviceToken t where t.user.id = :userId and t.fcmToken = :fcmToken")
    int deleteByUserIdAndFcmToken(Long userId, String fcmToken);

    interface PushTarget {
        Long getUserId();

        String getToken();
    }

    /** 이 사람들의 기기 토큰. 전체 알림을 끈 사람은 뺀다. */
    @Query("select t.user.id as userId, t.fcmToken as token from DeviceToken t "
            + "where t.user.id in :userIds and t.user.pushEnabled = true")
    List<PushTarget> findPushTargets(Collection<Long> userIds);

    /** FCM 이 없다고 한 토큰을 지운다. readAt 뒤에 다시 등록된 토큰(updated_at 이 더 늦음)은 남긴다. */
    @Modifying
    @Query("delete from DeviceToken t where t.fcmToken in :tokens and t.updatedAt <= :readAt")
    int deleteStale(Collection<String> tokens, Instant readAt);
}
