package com.buddygo.buddygoserver.domain.review.controller;

import com.buddygo.buddygoserver.domain.review.api.ReviewApi;
import com.buddygo.buddygoserver.domain.review.dto.request.ReviewSubmitRequest;
import com.buddygo.buddygoserver.domain.review.dto.response.PendingReviewsResponse;
import com.buddygo.buddygoserver.domain.review.dto.response.ReviewResponse;
import com.buddygo.buddygoserver.domain.review.dto.response.ReviewTagsResponse;
import com.buddygo.buddygoserver.domain.review.dto.response.ReviewTargetsResponse;
import com.buddygo.buddygoserver.domain.review.service.ReviewCommandService;
import com.buddygo.buddygoserver.domain.review.service.ReviewQueryService;
import com.buddygo.buddygoserver.global.response.success.SuccessResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ReviewController implements ReviewApi {

    private final ReviewCommandService reviewCommandService;
    private final ReviewQueryService reviewQueryService;

    @Override
    public ResponseEntity<SuccessResponse<PendingReviewsResponse>> getPending(Long userId) {
        return ResponseEntity.ok(SuccessResponse.from(reviewQueryService.getPending(userId)));
    }

    @Override
    public ResponseEntity<SuccessResponse<ReviewTagsResponse>> getTags() {
        return ResponseEntity.ok(SuccessResponse.from(reviewQueryService.getTags()));
    }

    @Override
    public ResponseEntity<SuccessResponse<ReviewTargetsResponse>> getTargets(Long userId, Long gatheringId) {
        return ResponseEntity.ok(SuccessResponse.from(reviewQueryService.getTargets(userId, gatheringId)));
    }

    @Override
    public ResponseEntity<SuccessResponse<ReviewResponse>> submit(
            Long userId, Long gatheringId, ReviewSubmitRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(SuccessResponse.created(reviewCommandService.submit(userId, gatheringId, request)));
    }
}
