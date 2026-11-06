package com.buddygo.buddygoserver.domain.buddy.exception;

import com.buddygo.buddygoserver.global.response.error.type.BaseErrorType;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * Buddy ErrorCode: Bxxx
 */
@Getter
@RequiredArgsConstructor
public enum BuddyErrorType implements BaseErrorType {

    REQUEST_NOT_FOUND(HttpStatus.NOT_FOUND, "B001", "버디 신청을 찾을 수 없습니다."),
    ALREADY_BUDDY(HttpStatus.CONFLICT, "B002", "이미 버디입니다."),
    REQUEST_DUPLICATED(HttpStatus.CONFLICT, "B003", "이미 보낸 버디 신청이 있습니다."),
    REQUEST_NOT_PENDING(HttpStatus.CONFLICT, "B004", "대기 중인 버디 신청이 아닙니다."),
    NOT_BUDDY(HttpStatus.FORBIDDEN, "B005", "버디만 조회할 수 있습니다."),
    SELF_REQUEST(HttpStatus.BAD_REQUEST, "B006", "자기 자신에게 버디 신청할 수 없습니다."),
    NOT_WORKOUT_PARTNER(HttpStatus.FORBIDDEN, "B007", "함께 운동한 사람에게만 버디 신청할 수 있습니다."),
    BUDDY_NOT_FOUND(HttpStatus.NOT_FOUND, "B008", "버디가 아닙니다."),
    ;

    private final HttpStatus status;
    private final String code;
    private final String message;
}
