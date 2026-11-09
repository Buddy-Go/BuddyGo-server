package com.buddygo.buddygoserver.domain.notification.repository;

import com.buddygo.buddygoserver.domain.notification.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    Slice<Notification> findByReceiverId(Long receiverId, Pageable pageable);

    long countByReceiverIdAndReadAtIsNull(Long receiverId);

    boolean existsByIdAndReceiverId(Long id, Long receiverId);

    /** 본인 알림만 갱신하고 이미 읽은 시각은 보존한다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Notification n set n.readAt = :now where n.id = :id and n.receiver.id = :receiverId and n.readAt is null")
    int markRead(Long id, Long receiverId, Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Notification n set n.readAt = :now where n.receiver.id = :receiverId and n.readAt is null")
    int markAllRead(Long receiverId, Instant now);

    /**
     * 모임당 한 번뿐인 알림(체크인 열림)을 넣는다. 같은 (받는 사람, 모임, 종류)가 있으면 건너뛴다.
     * 조건은 uk_notifications_once_per_gathering(V10) 조건과 같아야 한다. 네이티브 INSERT 라 created_at 을 넘긴다.
     */
    @Query(nativeQuery = true, value = """
            INSERT INTO notifications (receiver_id, type, gathering_id, message, created_at)
            VALUES (:receiverId, :type, :gatheringId, :message, :createdAt)
            ON CONFLICT (receiver_id, gathering_id, type) WHERE type IN ('CHECK_IN_OPENED') DO NOTHING
            RETURNING id""")
    List<Long> insertOncePerGatheringIgnoringDuplicate(Long receiverId, String type, Long gatheringId, String message,
                                                       Instant createdAt);
}
