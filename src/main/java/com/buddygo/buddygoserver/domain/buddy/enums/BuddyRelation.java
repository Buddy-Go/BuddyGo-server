package com.buddygo.buddygoserver.domain.buddy.enums;

/** 나와 상대의 버디 관계 (함께 운동한 사람 탭 · 프로필의 버디 버튼) */
public enum BuddyRelation {
    NONE,
    BUDDY,
    /** 내가 보낸 신청이 대기 중('신청함') */
    REQUEST_SENT,
    /** 상대가 보낸 신청이 대기 중 */
    REQUEST_RECEIVED
}
