package com.buddygo.buddygoserver.domain.notification.service;

import com.buddygo.buddygoserver.domain.notification.exception.NotificationErrorType;
import com.buddygo.buddygoserver.domain.notification.repository.NotificationRepository;
import com.buddygo.buddygoserver.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
@RequiredArgsConstructor
@Transactional
public class NotificationCommandService {
    private final NotificationRepository notificationRepository;
    private final Clock clock;

    public void read(Long userId, Long notificationId) {
        int updated = notificationRepository.markRead(notificationId, userId, Instant.now(clock));
        if (updated == 0 && !notificationRepository.existsByIdAndReceiverId(notificationId, userId)) {
            throw new CustomException(NotificationErrorType.NOT_FOUND);
        }
    }

    public void readAll(Long userId) {
        notificationRepository.markAllRead(userId, Instant.now(clock));
    }
}
