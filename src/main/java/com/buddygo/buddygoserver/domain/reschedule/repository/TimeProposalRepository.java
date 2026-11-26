package com.buddygo.buddygoserver.domain.reschedule.repository;

import com.buddygo.buddygoserver.domain.reschedule.entity.TimeProposal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TimeProposalRepository extends JpaRepository<TimeProposal, Long> {

    @Query("""
            select p from TimeProposal p join fetch p.proposer
            where p.id = :id and p.gathering.id = :gatheringId""")
    Optional<TimeProposal> findInGathering(Long id, Long gatheringId);

    @Query("""
            select p from TimeProposal p join fetch p.proposer
            where p.gathering.id = :gatheringId
              and p.status = com.buddygo.buddygoserver.domain.reschedule.enums.TimeProposalStatus.PENDING""")
    Optional<TimeProposal> findPending(Long gatheringId);

    /** 만료 작업 대상의 모임 id. 처리는 모임 행을 잠근 뒤 findPending 으로 다시 읽어 확인한다. */
    @Query("""
            select p.gathering.id from TimeProposal p
            where p.status = com.buddygo.buddygoserver.domain.reschedule.enums.TimeProposalStatus.PENDING
              and p.expiresAt <= :now
            order by p.expiresAt, p.id""")
    List<Long> findGatheringIdsWithExpiredPending(Instant now);
}
