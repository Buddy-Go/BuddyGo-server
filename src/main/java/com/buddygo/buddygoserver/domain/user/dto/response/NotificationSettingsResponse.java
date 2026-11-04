package com.buddygo.buddygoserver.domain.user.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record NotificationSettingsResponse(
        @Schema(example = "false") boolean pushEnabled
) {
}
