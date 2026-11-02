package com.buddygo.buddygoserver.global.response.error.type;

import org.springframework.http.HttpStatus;

/** 도메인별 ErrorType enum 이 구현하는 공통 계약 */
public interface BaseErrorType {

    HttpStatus getStatus();

    String getCode();

    String getMessage();
}
