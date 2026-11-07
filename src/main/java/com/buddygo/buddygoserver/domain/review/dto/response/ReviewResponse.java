package com.buddygo.buddygoserver.domain.review.dto.response;

import com.buddygo.buddygoserver.domain.review.entity.Review;
import com.buddygo.buddygoserver.domain.review.enums.ReviewRating;
import com.buddygo.buddygoserver.domain.review.enums.ReviewTagType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/** 제출한 평가. 매너온도 변화량은 상대에게도 나에게도 보여주지 않는다. */
public record ReviewResponse(
        @Schema(example = "10") Long reviewId,
        @Schema(example = "1") Long gatheringId,
        @Schema(example = "2") Long targetId,
        @Schema(example = "GOOD") ReviewRating rating,
        List<ReviewTagType> tags,
        String comment,
        @Schema(example = "2026-11-10T12:00:00Z") Instant createdAt
) {

    public static ReviewResponse of(Review review, List<ReviewTagType> tags) {
        return new ReviewResponse(review.getId(), review.getGathering().getId(), review.getTarget().getId(),
                review.getRating(), tags, review.getComment(), review.getCreatedAt());
    }
}
