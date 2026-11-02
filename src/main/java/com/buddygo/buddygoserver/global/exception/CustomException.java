package com.buddygo.buddygoserver.global.exception;

import com.buddygo.buddygoserver.global.response.error.type.BaseErrorType;
import lombok.Getter;

@Getter
public class CustomException extends RuntimeException {

    private final BaseErrorType errorType;

    public CustomException(BaseErrorType errorType) {
        super(errorType.getMessage());
        this.errorType = errorType;
    }

    /** 같은 에러 코드에 상황별 문구가 필요할 때 */
    public CustomException(BaseErrorType errorType, String message) {
        super(message);
        this.errorType = errorType;
    }
}
