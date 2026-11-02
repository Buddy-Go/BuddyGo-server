package com.buddygo.buddygoserver.domain.auth.exception;

import com.buddygo.buddygoserver.global.response.error.type.BaseErrorType;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * Auth ErrorCode: Axxx
 */
@Getter
@RequiredArgsConstructor
public enum AuthErrorType implements BaseErrorType {

    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "A001", "이메일 또는 비밀번호가 올바르지 않습니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "A002", "유효하지 않은 토큰입니다."),
    EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "A003", "만료된 토큰입니다."),
    REFRESH_TOKEN_REUSED(HttpStatus.UNAUTHORIZED, "A004", "이미 사용된 리프레시 토큰입니다. 다시 로그인해 주세요."),
    TOO_MANY_LOGIN_ATTEMPTS(HttpStatus.TOO_MANY_REQUESTS, "A005", "로그인 시도가 너무 많습니다. 잠시 후 다시 시도해 주세요."),
    ;

    private final HttpStatus status;
    private final String code;
    private final String message;
}
