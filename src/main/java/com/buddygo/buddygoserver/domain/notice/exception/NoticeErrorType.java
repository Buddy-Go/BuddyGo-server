package com.buddygo.buddygoserver.domain.notice.exception;

import com.buddygo.buddygoserver.global.response.error.type.BaseErrorType;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * Notice ErrorCode: Nxxx
 */
@Getter
@RequiredArgsConstructor
public enum NoticeErrorType implements BaseErrorType {

    NOT_FOUND(HttpStatus.NOT_FOUND, "N001", "공지를 찾을 수 없습니다."),
    COMMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "N002", "댓글을 찾을 수 없습니다."),
    COMMENT_DELETE_FORBIDDEN(HttpStatus.FORBIDDEN, "N003", "작성자 본인 또는 호스트만 댓글을 삭제할 수 있습니다."),
    ACCESS_FORBIDDEN(HttpStatus.FORBIDDEN, "N004", "호스트 또는 확정 참가자만 공지를 이용할 수 있습니다."),
    READ_ONLY(HttpStatus.CONFLICT, "N005", "종료·취소된 모임의 공지와 댓글은 읽기 전용입니다."),
    ;

    private final HttpStatus status;
    private final String code;
    private final String message;
}
