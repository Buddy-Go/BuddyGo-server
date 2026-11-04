package com.buddygo.buddygoserver.domain.sport.gym.enums;

/** 기준 일치 판정 (서비스 규칙 정의서 8장) */
public enum CriteriaMatch {
    /** 맞아요: 비교할 수 있는 부위가 모두 맞음 */
    MATCH,
    /** 일부만 맞아요 */
    PARTIAL,
    /** 맞지 않아요: 비교할 수 있는 부위가 하나도 안 맞음 */
    MISMATCH
}
