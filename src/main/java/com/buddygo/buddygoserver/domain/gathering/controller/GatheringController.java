package com.buddygo.buddygoserver.domain.gathering.controller;

import com.buddygo.buddygoserver.domain.gathering.api.GatheringApi;
import com.buddygo.buddygoserver.domain.gathering.dto.request.GatheringCreateRequest;
import com.buddygo.buddygoserver.domain.gathering.dto.response.GatheringDetailResponse;
import com.buddygo.buddygoserver.domain.gathering.service.GatheringCommandService;
import com.buddygo.buddygoserver.domain.gathering.service.GatheringQueryService;
import com.buddygo.buddygoserver.global.response.success.SuccessResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class GatheringController implements GatheringApi {

    private final GatheringCommandService gatheringCommandService;
    private final GatheringQueryService gatheringQueryService;

    @Override
    public ResponseEntity<SuccessResponse<GatheringDetailResponse>> create(Long userId, GatheringCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(SuccessResponse.created(gatheringCommandService.create(userId, request)));
    }

    @Override
    public ResponseEntity<SuccessResponse<GatheringDetailResponse>> getDetail(Long userId, Long gatheringId) {
        return ResponseEntity.ok(SuccessResponse.from(gatheringQueryService.getDetail(gatheringId, userId)));
    }
}
