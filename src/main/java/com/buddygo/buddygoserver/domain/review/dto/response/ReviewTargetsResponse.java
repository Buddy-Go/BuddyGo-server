package com.buddygo.buddygoserver.domain.review.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

public record ReviewTargetsResponse(
        @Schema(example = "1") Long gatheringId,
        @Schema(description = "이 시각부터는 평가할 수 없다(종료 + 3일)", example = "2026-11-13T11:30:00Z") Instant deadlineAt,
        @Schema(description = "지금 평가할 수 있는지(기한 전)", example = "true") boolean reviewable,
        @Schema(description = "체크인(정상·지각)한 다른 사람들. 호스트가 먼저") List<TargetResponse> targets
) {

    public record TargetResponse(
            @Schema(example = "2") Long userId,
            @Schema(example = "운동메이트") String nickname,
            @Schema(example = "false") boolean host,
            @Schema(description = "내가 이미 평가했는지", example = "false") boolean reviewed
    ) {
    }
}
