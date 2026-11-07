package com.buddygo.buddygoserver.domain.review.dto.response;

import com.buddygo.buddygoserver.domain.review.repository.ReviewRepository.PendingGathering;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.function.Function;

public record PendingReviewsResponse(List<PendingGatheringResponse> gatherings) {

    public static PendingReviewsResponse of(List<PendingGathering> pending, Function<Instant, Instant> deadlineOf) {
        return new PendingReviewsResponse(pending.stream()
                .map(p -> new PendingGatheringResponse(p.getGatheringId(), p.getTitle(), p.getStartAt(), p.getEndAt(),
                        deadlineOf.apply(p.getEndAt()), p.getRemainingCount()))
                .toList());
    }

    public record PendingGatheringResponse(
            @Schema(example = "1") Long gatheringId,
            @Schema(example = "저녁 가슴·팔 같이 하실 분") String title,
            @Schema(example = "2026-11-10T10:00:00Z") Instant startAt,
            @Schema(example = "2026-11-10T11:30:00Z") Instant endAt,
            @Schema(description = "평가 기한(이 시각부터 불가)", example = "2026-11-13T11:30:00Z") Instant deadlineAt,
            @Schema(description = "아직 평가하지 않은 사람 수", example = "2") long remainingCount
    ) {
    }
}
