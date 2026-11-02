package com.buddygo.buddygoserver.domain.review.exception;

import com.buddygo.buddygoserver.global.response.error.type.BaseErrorType;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * Review ErrorCode: Rxxx
 */
@Getter
@RequiredArgsConstructor
public enum ReviewErrorType implements BaseErrorType {

    NOT_AVAILABLE(HttpStatus.CONFLICT, "R001", "평가할 수 없는 모임입니다."),
    ALREADY_SUBMITTED(HttpStatus.CONFLICT, "R002", "이미 평가를 제출했습니다."),
    PERIOD_EXPIRED(HttpStatus.CONFLICT, "R003", "평가 기한이 지났습니다."),
    INVALID_TARGET(HttpStatus.BAD_REQUEST, "R004", "함께 참여한 사람만 평가할 수 있습니다."),
    ;

    private final HttpStatus status;
    private final String code;
    private final String message;
}
