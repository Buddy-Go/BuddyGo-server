package com.buddygo.buddygoserver.domain.notification.repository;

import com.buddygo.buddygoserver.domain.notification.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;

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
}
