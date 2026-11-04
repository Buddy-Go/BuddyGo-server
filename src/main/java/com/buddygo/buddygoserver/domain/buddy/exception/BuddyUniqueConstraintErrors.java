package com.buddygo.buddygoserver.domain.buddy.exception;

import com.buddygo.buddygoserver.global.exception.UniqueConstraintErrors;
import com.buddygo.buddygoserver.global.response.error.type.BaseErrorType;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class BuddyUniqueConstraintErrors implements UniqueConstraintErrors {

    @Override
    public Map<String, BaseErrorType> byConstraint() {
        return Map.of(
                "uk_buddy_requests_pending", BuddyErrorType.REQUEST_DUPLICATED,
                "buddies_pkey", BuddyErrorType.ALREADY_BUDDY);
    }
}
