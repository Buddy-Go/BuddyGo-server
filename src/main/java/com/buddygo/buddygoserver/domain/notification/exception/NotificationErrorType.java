package com.buddygo.buddygoserver.domain.notification.exception;

import com.buddygo.buddygoserver.global.response.error.type.BaseErrorType;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * Notification ErrorCode: NTxxx
 */
@Getter
@RequiredArgsConstructor
public enum NotificationErrorType implements BaseErrorType {

    NOT_FOUND(HttpStatus.NOT_FOUND, "NT001", "알림을 찾을 수 없습니다."),
    ;

    private final HttpStatus status;
    private final String code;
    private final String message;
}
