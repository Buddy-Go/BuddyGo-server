package com.buddygo.buddygoserver.domain.buddy.enums;

/**
 * 저장되는 상태는 PENDING · REJECTED 뿐이다. 수락·취소하면 신청 행을 지우므로
 * ACCEPTED · CANCELED 는 처리 결과를 응답할 때만 쓴다.
 */
public enum BuddyRequestStatus {
    PENDING,
    /** 응답 전용: 수락되어 버디가 됨(신청 행은 지워짐) */
    ACCEPTED,
    REJECTED,
    /** 응답 전용: 보낸 사람이 취소함(신청 행은 지워짐) */
    CANCELED
}
