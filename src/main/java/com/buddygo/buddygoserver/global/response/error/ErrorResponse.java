package com.buddygo.buddygoserver.global.response.error;

import com.buddygo.buddygoserver.global.response.error.type.BaseErrorType;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

/**
 * 에러 응답 형식: { "code": "GA001", "message": "...", "errors": { "field": "사유" } }
 * errors 는 입력값 검증 실패(G002)일 때만 실린다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        @Schema(example = "G002") String code,
        @Schema(example = "입력값이 올바르지 않습니다.") String message,
        @Schema(example = "{\"email\": \"이메일은 필수입니다.\"}") Map<String, String> errors
) {

    public static ErrorResponse from(BaseErrorType error) {
        return new ErrorResponse(error.getCode(), error.getMessage(), null);
    }

    public static ErrorResponse of(BaseErrorType error, String message) {
        return new ErrorResponse(error.getCode(), message, null);
    }

    public static ErrorResponse of(BaseErrorType error, Map<String, String> errors) {
        return new ErrorResponse(error.getCode(), error.getMessage(), errors);
    }
}
