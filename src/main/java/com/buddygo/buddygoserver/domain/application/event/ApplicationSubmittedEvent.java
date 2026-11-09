package com.buddygo.buddygoserver.domain.application.event;

/**
 * 참여 신청이 저장됐다. 신청과 같은 트랜잭션에서 동기로 발행된다. 지금은 버디 알림(BuddyActivityNotificationListener)이 받는다.
 * 신청 도메인이 버디를 알지 않도록 이벤트로 넘긴다.
 */
public record ApplicationSubmittedEvent(Long gatheringId, String title, Long hostId, Long applicantId,
                                        String applicantNickname) {
}
