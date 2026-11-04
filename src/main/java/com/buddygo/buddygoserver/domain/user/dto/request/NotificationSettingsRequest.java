package com.buddygo.buddygoserver.domain.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record NotificationSettingsRequest(
        @Schema(description = "전체 알림 ON/OFF", example = "false")
        @NotNull(message = "pushEnabled 는 필수입니다.")
        Boolean pushEnabled
) {
}
