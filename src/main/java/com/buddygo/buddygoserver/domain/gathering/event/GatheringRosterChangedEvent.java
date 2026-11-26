package com.buddygo.buddygoserver.domain.gathering.event;

/**
 * 모임의 확정 명단이 바뀌었거나(수락·나가기) 모임이 취소됐다. 바꾼 트랜잭션 안에서 모임 행을 잠근 채 동기로 발행된다.
 * 지금은 진행 중인 시간 변경 제안을 무산시키는 쪽(reschedule)이 받는다. 모임·신청 도메인이 제안을 알지 않도록 이벤트로 넘긴다.
 *
 * @param actorId 바꾼 사람(수락한 호스트, 나간 참가자, 취소한 호스트). 시스템이 바꿨으면 null
 */
public record GatheringRosterChangedEvent(Long gatheringId, Long actorId) {
}
