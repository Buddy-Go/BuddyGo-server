package com.buddygo.buddygoserver.domain.user.exception;

import com.buddygo.buddygoserver.global.exception.UniqueConstraintErrors;
import com.buddygo.buddygoserver.global.response.error.type.BaseErrorType;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class UserUniqueConstraintErrors implements UniqueConstraintErrors {

    @Override
    public Map<String, BaseErrorType> byConstraint() {
        return Map.of(
                "uk_users_email", UserErrorType.DUPLICATE_EMAIL,
                "uk_users_nickname", UserErrorType.DUPLICATE_NICKNAME);
    }
}
