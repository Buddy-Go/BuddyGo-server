package com.buddygo.buddygoserver.domain.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FcmTokenRequest(
        @Schema(description = "Firebase Cloud Messaging 등록 토큰", example = "dXNlci1kZXZpY2UtdG9rZW4...")
        @NotBlank(message = "FCM 토큰은 필수입니다.")
        @Size(max = 255, message = "FCM 토큰은 255자 이하여야 합니다.")
        String fcmToken
) {
}
