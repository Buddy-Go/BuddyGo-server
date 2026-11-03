package com.buddygo.buddygoserver.domain.auth.dto.request;

import com.buddygo.buddygoserver.domain.user.enums.Gender;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @Schema(example = "buddy@example.com")
        @NotBlank(message = "이메일은 필수입니다.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        @Size(max = 255, message = "이메일은 255자 이하여야 합니다.")
        String email,

        @Schema(example = "password123")
        @NotBlank(message = "비밀번호는 필수입니다.")
        @Pattern(regexp = PasswordPolicy.REGEX, message = PasswordPolicy.MESSAGE)
        String password,

        @Schema(example = "김버디")
        @NotBlank(message = "이름은 필수입니다.")
        @Size(max = 20, message = "이름은 20자 이하여야 합니다.")
        String name,

        @Schema(example = "헬스버디")
        @NotBlank(message = "닉네임은 필수입니다.")
        @Size(min = 2, max = 12, message = "닉네임은 2~12자여야 합니다.")
        String nickname,

        @Schema(example = "MALE")
        @NotNull(message = "성별은 필수입니다.")
        Gender gender
) {
}
