package com.buddygo.buddygoserver.domain.reschedule.repository;

import com.buddygo.buddygoserver.domain.reschedule.entity.TimeProposalVote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface TimeProposalVoteRepository extends JpaRepository<TimeProposalVote, Long> {

    /** 제안 시점 구성원의 응답. 호스트가 먼저 오도록 정렬은 부르는 쪽이 한다. */
    @Query("""
            select v from TimeProposalVote v join fetch v.user
            where v.proposal.id = :proposalId
            order by v.id""")
    List<TimeProposalVote> findWithUser(Long proposalId);
}
