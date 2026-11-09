package com.buddygo.buddygoserver.domain.review.dto.response;

import com.buddygo.buddygoserver.domain.review.dto.response.ReviewTagsResponse.TagResponse;
import com.buddygo.buddygoserver.domain.review.entity.Review;
import com.buddygo.buddygoserver.domain.review.enums.ReviewRating;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/** 받은 후기 한 줄. 아쉬움 평가(내 목록에만 나옴)는 누가 줬는지 드러나지 않게 평가자와 모임을 비운다. */
public record ReceivedReviewResponse(
        @Schema(example = "42") Long reviewId,
        @Schema(example = "GOOD") ReviewRating rating,
        @Schema(description = "한마디. 없으면 null", example = "덕분에 즐겁게 운동했어요") String comment,
        @Schema(description = "고른 태그. 없으면 빈 배열") List<TagResponse> tags,
        @Schema(example = "2026-11-10T00:00:00Z") Instant createdAt,
        @Schema(description = "평가한 사람. 아쉬움 평가는 null") EvaluatorResponse evaluator,
        @Schema(description = "평가한 모임. 아쉬움 평가는 null") GatheringResponse gathering
) {

    public static ReceivedReviewResponse of(Review review, List<TagResponse> tags) {
        boolean hidden = review.getRating() == ReviewRating.BAD;
        return new ReceivedReviewResponse(review.getId(), review.getRating(), review.getComment(), tags,
                review.getCreatedAt(),
                hidden ? null : new EvaluatorResponse(review.getEvaluator().getId(), review.getEvaluator().getNickname()),
                hidden ? null : new GatheringResponse(review.getGathering().getId(), review.getGathering().getTitle(),
                        review.getGathering().getStartAt()));
    }

    public record EvaluatorResponse(@Schema(example = "7") Long userId, @Schema(example = "헬스버디") String nickname) {
    }

    public record GatheringResponse(
            @Schema(example = "3") Long gatheringId,
            @Schema(example = "저녁 가슴 운동 같이 해요") String title,
            @Schema(example = "2026-11-09T10:00:00Z") Instant startAt
    ) {
    }
}
