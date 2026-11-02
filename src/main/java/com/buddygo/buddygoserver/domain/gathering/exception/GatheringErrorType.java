package com.buddygo.buddygoserver.domain.gathering.exception;

import com.buddygo.buddygoserver.global.response.error.type.BaseErrorType;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * Gathering ErrorCode: GAxxx
 */
@Getter
@RequiredArgsConstructor
public enum GatheringErrorType implements BaseErrorType {

    NOT_FOUND(HttpStatus.NOT_FOUND, "GA001", "모임을 찾을 수 없습니다."),
    NOT_HOST(HttpStatus.FORBIDDEN, "GA002", "호스트만 할 수 있습니다."),
    NOT_RECRUITING(HttpStatus.CONFLICT, "GA003", "모집 중인 모임이 아닙니다."),
    HAS_CONFIRMED_PARTICIPANTS(HttpStatus.CONFLICT, "GA004", "확정된 참가자가 있어 삭제할 수 없습니다."),
    EDIT_RESTRICTED(HttpStatus.CONFLICT, "GA005", "신청자가 있으면 제목·설명·상세주소만 수정할 수 있습니다."),
    PARTICIPANTS_NOT_VISIBLE(HttpStatus.FORBIDDEN, "GA006", "마감된 모임의 참가자만 명단을 볼 수 있습니다."),
    ;

    private final HttpStatus status;
    private final String code;
    private final String message;
}
