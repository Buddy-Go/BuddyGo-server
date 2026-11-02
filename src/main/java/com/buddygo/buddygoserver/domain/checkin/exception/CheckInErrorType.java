package com.buddygo.buddygoserver.domain.checkin.exception;

import com.buddygo.buddygoserver.global.response.error.type.BaseErrorType;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * Check-in ErrorCode: Cxxx
 */
@Getter
@RequiredArgsConstructor
public enum CheckInErrorType implements BaseErrorType {

    NOT_PARTICIPANT(HttpStatus.FORBIDDEN, "C001", "확정된 참가자만 체크인할 수 있습니다."),
    ALREADY_CHECKED_IN(HttpStatus.CONFLICT, "C002", "이미 체크인했습니다."),
    OUT_OF_RANGE(HttpStatus.UNPROCESSABLE_CONTENT, "C003", "체크인 가능 범위를 벗어났습니다."),
    NOT_AVAILABLE_TIME(HttpStatus.CONFLICT, "C004", "체크인할 수 있는 시간이 아닙니다."),
    ;

    private final HttpStatus status;
    private final String code;
    private final String message;
}
