package com.buddygo.buddygoserver.domain.reschedule.enums;

public enum TimeProposalStatus {
    /** 응답을 기다리는 중. 한 모임에 하나만 있다 */
    PENDING,
    /** 모두 수락해 시작 시각이 바뀌었다 */
    APPROVED,
    /** 한 명이 거절했다 */
    REJECTED,
    /** 제안자가 철회했다 */
    WITHDRAWN,
    /** 기한(체크인 열림 등) 안에 모두 수락하지 않았다 */
    EXPIRED,
    /** 확정 명단이 바뀌었거나 모임이 취소됐다 */
    CANCELED
}
