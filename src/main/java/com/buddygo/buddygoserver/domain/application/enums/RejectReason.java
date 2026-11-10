package com.buddygo.buddygoserver.domain.application.enums;

public enum RejectReason {
    BY_HOST,
    MEETING_FULL,
    MEETING_CANCELED,
    /** 호스트가 성별 조건을 바꿔 조건에 맞지 않게 됨 */
    GENDER_RULE_CHANGED
}
