package com.buddygo.buddygoserver.domain.gathering.event;

/**
 * 모임이 개설됐다. 개설과 같은 트랜잭션에서 동기로 발행된다. 지금은 버디 알림(BuddyActivityNotificationListener)이 받는다.
 * 모임 도메인이 버디를 알지 않도록 이벤트로 넘긴다.
 */
public record GatheringCreatedEvent(Long gatheringId, String title, Long hostId, String hostNickname) {
}
