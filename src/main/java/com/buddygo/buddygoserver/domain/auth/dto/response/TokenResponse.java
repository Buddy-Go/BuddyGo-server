package com.buddygo.buddygoserver.domain.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record TokenResponse(
        String accessToken,
        String refreshToken,
        @Schema(example = "Bearer") String tokenType,
        @Schema(description = "액세스 토큰 만료까지 남은 초", example = "1800") long accessTokenExpiresIn,
        @Schema(description = "리프레시 토큰 만료까지 남은 초", example = "1209600") long refreshTokenExpiresIn
) {

    public static TokenResponse of(String accessToken, long accessExpiresIn, String refreshToken, long refreshExpiresIn) {
        return new TokenResponse(accessToken, refreshToken, "Bearer", accessExpiresIn, refreshExpiresIn);
    }
}
