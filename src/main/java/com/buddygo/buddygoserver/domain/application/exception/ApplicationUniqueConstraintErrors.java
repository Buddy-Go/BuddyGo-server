package com.buddygo.buddygoserver.domain.application.exception;

import com.buddygo.buddygoserver.global.exception.UniqueConstraintErrors;
import com.buddygo.buddygoserver.global.response.error.type.BaseErrorType;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ApplicationUniqueConstraintErrors implements UniqueConstraintErrors {

    @Override
    public Map<String, BaseErrorType> byConstraint() {
        return Map.of("uk_participations_active", ApplicationErrorType.ALREADY_APPLIED);
    }
}
