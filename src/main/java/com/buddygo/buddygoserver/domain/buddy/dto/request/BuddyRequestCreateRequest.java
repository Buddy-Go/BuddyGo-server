package com.buddygo.buddygoserver.domain.buddy.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record BuddyRequestCreateRequest(
        @Schema(description = "신청 받을 사람", example = "3")
        @NotNull(message = "receiverId 는 필수입니다.")
        Long receiverId
) {
}
