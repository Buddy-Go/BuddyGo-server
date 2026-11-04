package com.buddygo.buddygoserver.domain.application.exception;

import com.buddygo.buddygoserver.global.response.error.type.BaseErrorType;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * Application (Participation) ErrorCode: APxxx
 */
@Getter
@RequiredArgsConstructor
public enum ApplicationErrorType implements BaseErrorType {

    NOT_FOUND(HttpStatus.NOT_FOUND, "AP001", "신청을 찾을 수 없습니다."),
    ALREADY_APPLIED(HttpStatus.CONFLICT, "AP002", "이미 신청했거나 참여 중인 모임입니다."),
    GATHERING_FULL(HttpStatus.CONFLICT, "AP003", "정원이 가득 찼습니다."),
    GENDER_RULE_MISMATCH(HttpStatus.FORBIDDEN, "AP004", "모임의 성별 조건에 맞지 않습니다."),
    NOT_PENDING(HttpStatus.CONFLICT, "AP005", "대기 중인 신청이 아닙니다."),
    HOST_CANNOT_APPLY(HttpStatus.CONFLICT, "AP006", "호스트는 자신의 모임에 신청할 수 없습니다."),
    NOT_PARTICIPANT(HttpStatus.FORBIDDEN, "AP007", "모임 참가자가 아닙니다."),
    HOST_CANNOT_LEAVE(HttpStatus.CONFLICT, "AP008", "호스트는 나갈 수 없습니다. 모임 취소를 이용해 주세요."),
    GYM_PART_NOT_IN_GATHERING(HttpStatus.BAD_REQUEST, "AP009", "모임에서 하는 부위의 기록만 입력할 수 있습니다."),
    ;

    private final HttpStatus status;
    private final String code;
    private final String message;
}
