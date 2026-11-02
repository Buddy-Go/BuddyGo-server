package com.buddygo.buddygoserver.global.response.success;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.http.HttpStatus;

/**
 * 성공 응답 형식: { "code": 200, "message": "...", "data": ... }
 * data 가 없으면 키 자체가 빠진다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SuccessResponse<T>(int code, String message, T data) {

    private static final String DEFAULT_MESSAGE = "요청이 성공하였습니다.";

    public static SuccessResponse<Void> ok() {
        return new SuccessResponse<>(HttpStatus.OK.value(), DEFAULT_MESSAGE, null);
    }

    public static <T> SuccessResponse<T> from(T data) {
        return new SuccessResponse<>(HttpStatus.OK.value(), DEFAULT_MESSAGE, data);
    }

    public static <T> SuccessResponse<T> created(T data) {
        return new SuccessResponse<>(HttpStatus.CREATED.value(), DEFAULT_MESSAGE, data);
    }
}
