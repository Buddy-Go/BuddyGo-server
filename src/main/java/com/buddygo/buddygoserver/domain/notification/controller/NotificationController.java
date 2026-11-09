package com.buddygo.buddygoserver.domain.notification.controller;

import com.buddygo.buddygoserver.domain.notification.api.NotificationApi;
import com.buddygo.buddygoserver.domain.notification.dto.response.NotificationListResponse;
import com.buddygo.buddygoserver.domain.notification.service.NotificationCommandService;
import com.buddygo.buddygoserver.domain.notification.service.NotificationQueryService;
import com.buddygo.buddygoserver.global.response.success.SuccessResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class NotificationController implements NotificationApi {
    private final NotificationQueryService queryService;
    private final NotificationCommandService commandService;

    @Override
    public ResponseEntity<SuccessResponse<NotificationListResponse>> getList(Long userId, int page, int size) {
        return ResponseEntity.ok(SuccessResponse.from(queryService.getList(userId, page, size)));
    }

    @Override
    public ResponseEntity<SuccessResponse<Void>> read(Long userId, Long notificationId) {
        commandService.read(userId, notificationId);
        return ResponseEntity.ok(SuccessResponse.ok());
    }

    @Override
    public ResponseEntity<SuccessResponse<Void>> readAll(Long userId) {
        commandService.readAll(userId);
        return ResponseEntity.ok(SuccessResponse.ok());
    }
}
