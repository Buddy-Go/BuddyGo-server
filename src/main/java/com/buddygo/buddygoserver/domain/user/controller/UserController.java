package com.buddygo.buddygoserver.domain.user.controller;

import com.buddygo.buddygoserver.domain.user.api.UserApi;
import com.buddygo.buddygoserver.domain.user.dto.request.FcmTokenRequest;
import com.buddygo.buddygoserver.domain.user.dto.request.NotificationSettingsRequest;
import com.buddygo.buddygoserver.domain.user.dto.response.MyInfoResponse;
import com.buddygo.buddygoserver.domain.user.dto.response.NotificationSettingsResponse;
import com.buddygo.buddygoserver.domain.user.service.UserCommandService;
import com.buddygo.buddygoserver.domain.user.service.UserQueryService;
import com.buddygo.buddygoserver.global.response.success.SuccessResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class UserController implements UserApi {

    private final UserCommandService userCommandService;
    private final UserQueryService userQueryService;

    @Override
    public ResponseEntity<SuccessResponse<MyInfoResponse>> getMe(Long userId) {
        return ResponseEntity.ok(SuccessResponse.from(userQueryService.getMe(userId)));
    }

    @Override
    public ResponseEntity<SuccessResponse<NotificationSettingsResponse>> changeNotificationSettings(
            Long userId, NotificationSettingsRequest request) {
        return ResponseEntity.ok(SuccessResponse.from(
                userCommandService.changeNotificationSettings(userId, request.pushEnabled())));
    }

    @Override
    public ResponseEntity<SuccessResponse<Void>> registerFcmToken(Long userId, FcmTokenRequest request) {
        userCommandService.registerFcmToken(userId, request.fcmToken());
        return ResponseEntity.ok(SuccessResponse.ok());
    }
}
