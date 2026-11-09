package com.buddygo.buddygoserver.domain.user.controller;

import com.buddygo.buddygoserver.domain.user.api.UserApi;
import com.buddygo.buddygoserver.domain.user.dto.request.FcmTokenRequest;
import com.buddygo.buddygoserver.domain.user.dto.request.NotificationSettingsRequest;
import com.buddygo.buddygoserver.domain.user.dto.response.NotificationSettingsResponse;
import com.buddygo.buddygoserver.domain.user.service.UserCommandService;
import com.buddygo.buddygoserver.domain.user.service.MyActivityQueryService;
import com.buddygo.buddygoserver.domain.user.dto.response.MyActivitiesResponse;
import com.buddygo.buddygoserver.global.response.success.SuccessResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class UserController implements UserApi {

    private final UserCommandService userCommandService;
    private final MyActivityQueryService myActivityQueryService;

    @Override
    public ResponseEntity<SuccessResponse<MyActivitiesResponse>> getActivities(
            Long userId, String role, String period, String sport, int page, int size) {
        return ResponseEntity.ok(SuccessResponse.from(
                myActivityQueryService.getActivities(userId, role, period, page, size)));
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
