package com.buddygo.buddygoserver.domain.reschedule.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record TimeProposalCreateRequest(
        @Schema(description = "새 시작 시각(UTC). 10분 단위 정각, 지금 시작 시각 ±1시간 안. 진행 시간은 그대로다.",
                example = "2026-11-10T10:30:00Z")
        @NotNull(message = "새 시작 시각은 필수입니다.")
        Instant startAt
) {
}
