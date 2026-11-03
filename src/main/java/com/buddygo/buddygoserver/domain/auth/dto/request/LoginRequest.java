package com.buddygo.buddygoserver.domain.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @Schema(example = "buddy@example.com")
        @NotBlank(message = "이메일은 필수입니다.")
        String email,

        @Schema(example = "password123")
        @NotBlank(message = "비밀번호는 필수입니다.")
        String password
) {
}
