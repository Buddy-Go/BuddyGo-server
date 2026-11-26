package com.buddygo.buddygoserver.domain.reschedule.entity;

import com.buddygo.buddygoserver.domain.reschedule.enums.VoteDecision;
import com.buddygo.buddygoserver.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** 제안 시점 구성원 한 명의 응답. 이 행들이 '모두 수락'을 판정하는 명단이다. */
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Entity
@Table(name = "gathering_time_proposal_votes")
public class TimeProposalVote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "proposal_id", nullable = false)
    private TimeProposal proposal;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VoteDecision decision;

    @Column(name = "responded_at")
    private Instant respondedAt;

    /** 제안자는 처음부터 수락한 것으로 친다. */
    public static TimeProposalVote of(TimeProposal proposal, User user, boolean proposer, Instant now) {
        return TimeProposalVote.builder()
                .proposal(proposal)
                .user(user)
                .decision(proposer ? VoteDecision.ACCEPTED : VoteDecision.PENDING)
                .respondedAt(proposer ? now : null)
                .build();
    }

    public boolean isPending() {
        return decision == VoteDecision.PENDING;
    }

    public boolean isAccepted() {
        return decision == VoteDecision.ACCEPTED;
    }

    public void decide(boolean accept, Instant now) {
        this.decision = accept ? VoteDecision.ACCEPTED : VoteDecision.REJECTED;
        this.respondedAt = now;
    }
}
