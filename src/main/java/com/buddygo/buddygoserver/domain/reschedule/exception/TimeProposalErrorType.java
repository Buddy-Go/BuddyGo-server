package com.buddygo.buddygoserver.domain.reschedule.exception;

import com.buddygo.buddygoserver.global.response.error.type.BaseErrorType;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * Time proposal ErrorCode: TPxxx
 */
@Getter
@RequiredArgsConstructor
public enum TimeProposalErrorType implements BaseErrorType {

    NOT_FOUND(HttpStatus.NOT_FOUND, "TP001", "시간 변경 제안을 찾을 수 없습니다."),
    NOT_MEMBER(HttpStatus.FORBIDDEN, "TP002", "호스트 또는 확정 참가자만 할 수 있습니다."),
    NO_CONFIRMED_PARTICIPANTS(HttpStatus.CONFLICT, "TP003", "확정 참가자가 없는 모임은 제안 없이 모임 수정으로 시간을 바꿉니다."),
    ALREADY_PENDING(HttpStatus.CONFLICT, "TP004", "이미 진행 중인 시간 변경 제안이 있습니다."),
    INVALID_START_AT(HttpStatus.BAD_REQUEST, "TP005", "제안할 수 없는 시작 시각입니다."),
    TOO_LATE(HttpStatus.CONFLICT, "TP006", "지금은 시간 변경을 제안할 수 없습니다."),
    NOT_PENDING(HttpStatus.CONFLICT, "TP007", "이미 끝난 제안입니다."),
    EXPIRED(HttpStatus.CONFLICT, "TP008", "응답 기한이 지난 제안입니다."),
    ALREADY_RESPONDED(HttpStatus.CONFLICT, "TP009", "이미 응답한 제안입니다."),
    NOT_PROPOSER(HttpStatus.FORBIDDEN, "TP010", "제안한 사람만 철회할 수 있습니다."),
    ;

    private final HttpStatus status;
    private final String code;
    private final String message;
}
