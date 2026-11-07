package com.buddygo.buddygoserver.domain.gathering.controller;

import com.buddygo.buddygoserver.domain.gathering.api.GatheringApi;
import com.buddygo.buddygoserver.domain.gathering.dto.request.GatheringCancelRequest;
import com.buddygo.buddygoserver.domain.gathering.dto.request.GatheringCreateRequest;
import com.buddygo.buddygoserver.domain.gathering.dto.request.GatheringUpdateRequest;
import com.buddygo.buddygoserver.domain.gathering.dto.response.GatheringDetailResponse;
import com.buddygo.buddygoserver.domain.gathering.dto.response.GatheringDetailResponse.MemberResponse;
import com.buddygo.buddygoserver.domain.gathering.service.GatheringCommandService;
import com.buddygo.buddygoserver.domain.gathering.service.GatheringQueryService;
import com.buddygo.buddygoserver.domain.gathering.service.GatheringLikeCommandService;
import com.buddygo.buddygoserver.global.response.success.SuccessResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class GatheringController implements GatheringApi {

    private final GatheringCommandService gatheringCommandService;
    private final GatheringQueryService gatheringQueryService;
    private final GatheringLikeCommandService gatheringLikeCommandService;

    @Override
    public ResponseEntity<SuccessResponse<Void>> like(Long userId, Long gatheringId) {
        gatheringLikeCommandService.like(userId, gatheringId);
        return ResponseEntity.ok(SuccessResponse.ok());
    }

    @Override
    public ResponseEntity<SuccessResponse<Void>> unlike(Long userId, Long gatheringId) {
        gatheringLikeCommandService.unlike(userId, gatheringId);
        return ResponseEntity.ok(SuccessResponse.ok());
    }

    @Override
    public ResponseEntity<SuccessResponse<GatheringDetailResponse>> create(Long userId, GatheringCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(SuccessResponse.created(gatheringCommandService.create(userId, request)));
    }

    @Override
    public ResponseEntity<SuccessResponse<GatheringDetailResponse>> getDetail(Long userId, Long gatheringId) {
        return ResponseEntity.ok(SuccessResponse.from(gatheringQueryService.getDetail(gatheringId, userId)));
    }

    @Override
    public ResponseEntity<SuccessResponse<List<MemberResponse>>> getParticipants(Long userId, Long gatheringId) {
        return ResponseEntity.ok(SuccessResponse.from(gatheringQueryService.getParticipants(gatheringId, userId)));
    }

    @Override
    public ResponseEntity<SuccessResponse<GatheringDetailResponse>> update(
            Long userId, Long gatheringId, GatheringUpdateRequest request) {
        return ResponseEntity.ok(SuccessResponse.from(gatheringCommandService.update(userId, gatheringId, request)));
    }

    @Override
    public ResponseEntity<SuccessResponse<Void>> delete(Long userId, Long gatheringId) {
        gatheringCommandService.delete(userId, gatheringId);
        return ResponseEntity.ok(SuccessResponse.ok());
    }

    @Override
    public ResponseEntity<SuccessResponse<Void>> cancel(Long userId, Long gatheringId, GatheringCancelRequest request) {
        gatheringCommandService.cancel(userId, gatheringId, request.reason());
        return ResponseEntity.ok(SuccessResponse.ok());
    }
}
