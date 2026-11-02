package com.buddygo.buddygoserver.domain.user.exception;

import com.buddygo.buddygoserver.global.response.error.type.BaseErrorType;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * User ErrorCode: Uxxx
 */
@Getter
@RequiredArgsConstructor
public enum UserErrorType implements BaseErrorType {

    NOT_FOUND(HttpStatus.NOT_FOUND, "U001", "사용자를 찾을 수 없습니다."),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "U002", "이미 사용 중인 이메일입니다."),
    DUPLICATE_NICKNAME(HttpStatus.CONFLICT, "U003", "이미 사용 중인 닉네임입니다."),
    ;

    private final HttpStatus status;
    private final String code;
    private final String message;
}
