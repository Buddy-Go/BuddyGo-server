package com.buddygo.buddygoserver.global.response.error.type;

import com.buddygo.buddygoserver.global.response.error.type.BaseErrorType;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * Global ErrorCode: Gxxx
 */
@Getter
@RequiredArgsConstructor
public enum GlobalErrorType implements BaseErrorType {

    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "G001", "서버 오류가 발생했습니다."),
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "G002", "입력값이 올바르지 않습니다."),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "G003", "요청 본문을 읽을 수 없습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "G004", "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "G005", "접근 권한이 없습니다."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "G006", "요청한 리소스를 찾을 수 없습니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "G007", "지원하지 않는 HTTP 메서드입니다."),
    ;

    private final HttpStatus status;
    private final String code;
    private final String message;
}
