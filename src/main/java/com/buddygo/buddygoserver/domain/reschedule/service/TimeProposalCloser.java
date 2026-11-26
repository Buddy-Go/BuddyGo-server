package com.buddygo.buddygoserver.domain.reschedule.service;

import com.buddygo.buddygoserver.domain.application.repository.ParticipationRepository;
import com.buddygo.buddygoserver.domain.gathering.entity.Gathering;
import com.buddygo.buddygoserver.domain.reschedule.entity.TimeProposal;
import com.buddygo.buddygoserver.domain.reschedule.entity.TimeProposalVote;
import com.buddygo.buddygoserver.domain.reschedule.enums.TimeProposalStatus;
import com.buddygo.buddygoserver.domain.reschedule.repository.TimeProposalVoteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 사람의 응답이 아닌 이유(만료, 명단 변경, 모임 취소)로 제안을 끝낸다. 모임 행을 잠근 트랜잭션 안에서만 부른다. */
@Component
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class TimeProposalCloser {

    private final ParticipationRepository participationRepository;
    private final TimeProposalVoteRepository timeProposalVoteRepository;
    private final TimeProposalNotifier notifier;

    /** 기한 안에 모두 수락하지 않았다. 모임이 취소됐으면 취소 알림을 이미 받았으므로 알리지 않는다. */
    public void expire(TimeProposal proposal, Gathering gathering, Instant now) {
        proposal.resolve(TimeProposalStatus.EXPIRED, now);
        if (!gathering.isCanceled()) {
            notifier.expired(proposal, timeProposalVoteRepository.findWithUser(proposal.getId()));
        }
    }

    /**
     * 제안 시점 구성원과 지금 확정 명단(호스트 포함)이 다르면 무산시키고 true 를 돌려준다. 모임이 취소됐으면 알리지 않고,
     * 아니면 남은 구성원(제안 시점 구성원 중 아직 확정인 사람)에게 알린다.
     */
    public boolean cancelIfRosterChanged(TimeProposal proposal, Gathering gathering, List<TimeProposalVote> votes,
                                         Long actorId, Instant now) {
        Set<Long> confirmed = new HashSet<>(participationRepository.findConfirmedUserIds(gathering.getId()));
        Set<Long> voters = new HashSet<>(votes.stream().map(v -> v.getUser().getId()).toList());
        if (!gathering.isCanceled() && confirmed.equals(voters)) {
            return false;
        }
        proposal.resolve(TimeProposalStatus.CANCELED, now);
        if (!gathering.isCanceled()) {
            voters.retainAll(confirmed);
            notifier.rosterChanged(proposal, voters, actorId);
        }
        return true;
    }
}
