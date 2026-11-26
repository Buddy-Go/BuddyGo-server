package com.buddygo.buddygoserver.domain.reschedule.service;

import com.buddygo.buddygoserver.domain.gathering.repository.GatheringRepository;
import com.buddygo.buddygoserver.domain.reschedule.repository.TimeProposalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/** 기한이 지난 제안을 모임마다 따로 트랜잭션을 열어 만료시킨다(TimeProposalExpiryScheduler). */
@Service
@RequiredArgsConstructor
public class TimeProposalExpiryService {

    private final GatheringRepository gatheringRepository;
    private final TimeProposalRepository timeProposalRepository;
    private final TimeProposalCloser closer;

    @Transactional(readOnly = true)
    public List<Long> findGatheringIdsWithExpired(Instant now) {
        return timeProposalRepository.findGatheringIdsWithExpiredPending(now);
    }

    /** 모임 행을 잠근 뒤 다시 읽어, 그사이 통과·거절·무산된 제안은 건너뛴다. */
    @Transactional
    public void expire(Long gatheringId, Instant now) {
        gatheringRepository.findByIdForUpdate(gatheringId).ifPresent(gathering ->
                timeProposalRepository.findPending(gatheringId)
                        .filter(p -> p.isExpiredAt(now))
                        .ifPresent(p -> closer.expire(p, gathering, now)));
    }
}
