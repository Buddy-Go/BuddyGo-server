package com.buddygo.buddygoserver.domain.notification.service;

import com.buddygo.buddygoserver.domain.notification.dto.response.NotificationListResponse;
import com.buddygo.buddygoserver.domain.notification.dto.response.NotificationResponse;
import com.buddygo.buddygoserver.domain.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationQueryService {
    private final NotificationRepository notificationRepository;

    public NotificationListResponse getList(Long userId, int page, int size) {
        var notifications = notificationRepository.findByReceiverId(userId,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id")));
        return new NotificationListResponse(notifications.map(NotificationResponse::from).getContent(),
                page, size, notifications.hasNext(), notificationRepository.countByReceiverIdAndReadAtIsNull(userId));
    }
}
