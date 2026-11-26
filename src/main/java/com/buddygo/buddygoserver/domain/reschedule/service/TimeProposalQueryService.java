package com.buddygo.buddygoserver.domain.reschedule.service;

import com.buddygo.buddygoserver.domain.application.repository.ParticipationRepository;
import com.buddygo.buddygoserver.domain.gathering.entity.Gathering;
import com.buddygo.buddygoserver.domain.gathering.exception.GatheringErrorType;
import com.buddygo.buddygoserver.domain.gathering.repository.GatheringRepository;
import com.buddygo.buddygoserver.domain.reschedule.dto.response.TimeProposalResponse;
import com.buddygo.buddygoserver.domain.reschedule.exception.TimeProposalErrorType;
import com.buddygo.buddygoserver.domain.reschedule.repository.TimeProposalRepository;
import com.buddygo.buddygoserver.domain.reschedule.repository.TimeProposalVoteRepository;
import com.buddygo.buddygoserver.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TimeProposalQueryService {

    private final GatheringRepository gatheringRepository;
    private final ParticipationRepository participationRepository;
    private final TimeProposalRepository timeProposalRepository;
    private final TimeProposalVoteRepository timeProposalVoteRepository;

    /** 진행 중인 제안. 없으면 null. 호스트와 확정 참가자만 본다. */
    public TimeProposalResponse current(Long userId, Long gatheringId) {
        Gathering gathering = gatheringRepository.findById(gatheringId)
                .orElseThrow(() -> new CustomException(GatheringErrorType.NOT_FOUND));
        boolean member = gathering.isHostedBy(userId) || participationRepository.findActive(gatheringId, userId)
                .filter(p -> p.isConfirmed()).isPresent();
        if (!member) {
            throw new CustomException(TimeProposalErrorType.NOT_MEMBER);
        }
        return timeProposalRepository.findPending(gatheringId)
                .map(p -> TimeProposalResponse.of(p, timeProposalVoteRepository.findWithUser(p.getId())))
                .orElse(null);
    }
}
