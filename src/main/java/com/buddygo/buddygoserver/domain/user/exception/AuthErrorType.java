package com.buddygo.buddygoserver.domain.user.exception;

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
    INVALID_SIGNUP_TOKEN(HttpStatus.UNAUTHORIZED, "A004", "유효하지 않은 가입용 토큰입니다."),
    UNSUPPORTED_PROVIDER(HttpStatus.BAD_REQUEST, "A005", "지원하지 않는 소셜 로그인입니다."),
    ;

    private final HttpStatus status;
    private final String code;
    private final String message;
}
