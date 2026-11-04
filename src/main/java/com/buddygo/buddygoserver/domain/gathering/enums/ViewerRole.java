package com.buddygo.buddygoserver.domain.gathering.enums;

/** 모임 상세를 보는 사람의 위치. 보이는 정보가 달라진다 (서비스 규칙 정의서 5장 참가자 명단 공개). */
public enum ViewerRole {
    HOST,
    /** 확정 참가자 */
    PARTICIPANT,
    /** 대기 중인 신청자 */
    APPLICANT,
    NONE
}
