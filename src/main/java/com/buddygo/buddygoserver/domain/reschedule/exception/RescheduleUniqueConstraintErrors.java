package com.buddygo.buddygoserver.domain.reschedule.exception;

import com.buddygo.buddygoserver.global.exception.UniqueConstraintErrors;
import com.buddygo.buddygoserver.global.response.error.type.BaseErrorType;
import org.springframework.stereotype.Component;

import java.util.Map;

/** 제안은 모임 행을 잠근 뒤 검사하고 넣지만, 진행 중 제안 하나 규칙의 마지막 방어선으로 매핑해 둔다. */
@Component
public class RescheduleUniqueConstraintErrors implements UniqueConstraintErrors {

    @Override
    public Map<String, BaseErrorType> byConstraint() {
        return Map.of("uk_gathering_time_proposals_pending", TimeProposalErrorType.ALREADY_PENDING);
    }
}
