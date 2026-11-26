package com.buddygo.buddygoserver.domain.application.enums;

public enum RejectReason {
    BY_HOST,
    MEETING_FULL,
    MEETING_CANCELED,
    /** 호스트가 성별 조건을 바꿔 조건에 맞지 않게 됨 */
    GENDER_RULE_CHANGED,
    /** 호스트가 데드라인 전에 일시·장소를 바꿈. 바뀐 일정을 보고 다시 신청한다 */
    SCHEDULE_CHANGED
}
