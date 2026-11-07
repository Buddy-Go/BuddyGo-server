package com.buddygo.buddygoserver.domain.gathering.controller;

import com.buddygo.buddygoserver.domain.gathering.api.GatheringSearchApi;
import com.buddygo.buddygoserver.domain.gathering.dto.request.GatheringSearchRequest;
import com.buddygo.buddygoserver.domain.gathering.dto.response.GatheringPinsResponse;
import com.buddygo.buddygoserver.domain.gathering.dto.response.GatheringSearchResponse;
import com.buddygo.buddygoserver.domain.gathering.service.GatheringSearchQueryService;
import com.buddygo.buddygoserver.global.response.success.SuccessResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class GatheringSearchController implements GatheringSearchApi {

    private final GatheringSearchQueryService gatheringSearchQueryService;

    @Override
    public ResponseEntity<SuccessResponse<GatheringSearchResponse>> search(
            Long userId, GatheringSearchRequest request, String cursor, int size) {
        return ResponseEntity.ok(SuccessResponse.from(gatheringSearchQueryService.search(userId, request, cursor, size)));
    }

    @Override
    public ResponseEntity<SuccessResponse<GatheringPinsResponse>> pins(Long userId, GatheringSearchRequest request) {
        return ResponseEntity.ok(SuccessResponse.from(gatheringSearchQueryService.pins(userId, request)));
    }
}
