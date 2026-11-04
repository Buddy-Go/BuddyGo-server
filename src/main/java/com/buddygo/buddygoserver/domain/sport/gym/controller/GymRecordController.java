package com.buddygo.buddygoserver.domain.sport.gym.controller;

import com.buddygo.buddygoserver.domain.sport.gym.api.GymRecordApi;
import com.buddygo.buddygoserver.domain.sport.gym.dto.request.GymRecordsRequest;
import com.buddygo.buddygoserver.domain.sport.gym.dto.response.GymProfileResponse;
import com.buddygo.buddygoserver.domain.sport.gym.service.GymRecordCommandService;
import com.buddygo.buddygoserver.domain.sport.gym.service.GymRecordQueryService;
import com.buddygo.buddygoserver.global.response.success.SuccessResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class GymRecordController implements GymRecordApi {

    private final GymRecordCommandService gymRecordCommandService;
    private final GymRecordQueryService gymRecordQueryService;

    @Override
    public ResponseEntity<SuccessResponse<GymProfileResponse>> getMyProfile(Long userId) {
        return ResponseEntity.ok(SuccessResponse.from(gymRecordQueryService.getMyRecords(userId)));
    }

    @Override
    public ResponseEntity<SuccessResponse<GymProfileResponse>> replaceMyGymRecords(Long userId, GymRecordsRequest request) {
        return ResponseEntity.ok(SuccessResponse.from(gymRecordCommandService.replaceMyRecords(userId, request)));
    }
}
