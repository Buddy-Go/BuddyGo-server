package com.buddygo.buddygoserver.domain.checkin.exception;

import com.buddygo.buddygoserver.global.exception.UniqueConstraintErrors;
import com.buddygo.buddygoserver.global.response.error.type.BaseErrorType;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class CheckInUniqueConstraintErrors implements UniqueConstraintErrors {

    @Override
    public Map<String, BaseErrorType> byConstraint() {
        return Map.of("check_ins_pkey", CheckInErrorType.ALREADY_CHECKED_IN);
    }
}
