package com.buddygo.buddygoserver.domain.notification.enums;

/**
 * 알림 종류(서비스 규칙 정의서 12장). notifications.type 에 이름 그대로 저장한다. 엔티티는 문자열로 읽으므로
 * 여기서 이름을 바꾸거나 지우면 이미 저장된 알림의 type 은 앱이 모르는 값이 된다.
 */
public enum NotificationType {
    // 신청
    /** 참가자가 모임 신청 → 호스트 */
    APPLICATION_RECEIVED,
    /** 신청 수락 → 신청자 */
    APPLICATION_ACCEPTED,
    /** 신청 거절(호스트 거절, 정원 마감·모임 취소·삭제로 자동 거절) → 신청자 */
    APPLICATION_REJECTED,
    // 마감
    /** 정원이 다 차 모임 확정 → 호스트 + 확정 참가자 */
    GATHERING_CLOSED,
    /** 참가자가 데드라인 전에 나가 다시 모집 → 호스트 + 남은 확정 참가자 */
    MEMBER_LEFT_REOPENED,
    // 취소
    /** 참가자가 데드라인 후에 나가 모임 취소(방폭) → 호스트 + 남은 확정 참가자 */
    GATHERING_CANCELED_MEMBER_LEFT,
    /** 데드라인까지 정원 미달로 자동 취소 → 호스트 + 확정 참가자 */
    GATHERING_CANCELED_UNDER_CAPACITY,
    /** 호스트 취소(사유 포함) → 확정 참가자 */
    GATHERING_CANCELED_BY_HOST,
    // 진행
    /** 시작 10분 전 체크인 열림 → 호스트 + 확정 참가자. 모임당 한 번(부분 유니크 인덱스, V10) */
    CHECK_IN_OPENED,
    /** 운동 시작 → 아직 체크인하지 않은 호스트·참가자 */
    GATHERING_STARTED,
    /** 모임 종료 · 평가 가능 → 체크인(정상·지각)한 사람 */
    GATHERING_FINISHED,
    // 공지
    /** 공지 작성 → 확정 참가자 */
    NOTICE_CREATED,
    /** 공지 수정 → 확정 참가자 */
    NOTICE_UPDATED,
    /** 공지 댓글 → 호스트(내 공지), 그 공지에 댓글 단 참가자 */
    NOTICE_COMMENT_CREATED,
    // 버디
    /** 버디 신청 받음 → 받은 사람 */
    BUDDY_REQUEST_RECEIVED,
    /** 버디 수락 → 신청한 사람 */
    BUDDY_REQUEST_ACCEPTED,
    /** 버디가 모임 개설 → 그 버디의 개설 알림을 켜둔 버디 */
    BUDDY_GATHERING_CREATED,
    /** 버디가 모임 신청 → 그 버디의 신청 알림을 켜둔 버디 */
    BUDDY_GATHERING_APPLIED
}
