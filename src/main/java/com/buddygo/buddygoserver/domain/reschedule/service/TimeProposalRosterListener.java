package com.buddygo.buddygoserver.domain.reschedule.service;

import com.buddygo.buddygoserver.domain.gathering.event.GatheringRosterChangedEvent;
import com.buddygo.buddygoserver.domain.gathering.repository.GatheringRepository;
import com.buddygo.buddygoserver.domain.reschedule.repository.TimeProposalRepository;
import com.buddygo.buddygoserver.domain.reschedule.repository.TimeProposalVoteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * 확정 명단이 바뀌거나 모임이 취소되면 진행 중인 제안을 무산시킨다. 새로 들어온 사람은 그 시각에 동의한 적이 없고,
 * 나간 사람의 수락은 더 이상 의미가 없기 때문이다. 발행한 트랜잭션(모임 행을 잠근 상태) 안에서 동기로 돈다.
 */
@Component
@RequiredArgsConstructor
public class TimeProposalRosterListener {

    private final GatheringRepository gatheringRepository;
    private final TimeProposalRepository timeProposalRepository;
    private final TimeProposalVoteRepository timeProposalVoteRepository;
    private final TimeProposalCloser closer;
    private final Clock clock;

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void onRosterChanged(GatheringRosterChangedEvent event) {
        timeProposalRepository.findPending(event.gatheringId()).ifPresent(proposal -> closer.cancelIfRosterChanged(
                proposal, gatheringRepository.getReferenceById(event.gatheringId()),
                timeProposalVoteRepository.findWithUser(proposal.getId()), event.actorId(), Instant.now(clock)));
    }
}
