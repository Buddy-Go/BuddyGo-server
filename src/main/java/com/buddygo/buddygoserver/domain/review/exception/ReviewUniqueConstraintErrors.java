package com.buddygo.buddygoserver.domain.review.exception;

import com.buddygo.buddygoserver.global.exception.UniqueConstraintErrors;
import com.buddygo.buddygoserver.global.response.error.type.BaseErrorType;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ReviewUniqueConstraintErrors implements UniqueConstraintErrors {

    @Override
    public Map<String, BaseErrorType> byConstraint() {
        return Map.of("uk_reviews_gathering_evaluator_target", ReviewErrorType.ALREADY_SUBMITTED);
    }
}
