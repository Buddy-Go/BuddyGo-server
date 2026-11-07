package com.buddygo.buddygoserver.domain.notification.repository;

import com.buddygo.buddygoserver.domain.notification.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
}
