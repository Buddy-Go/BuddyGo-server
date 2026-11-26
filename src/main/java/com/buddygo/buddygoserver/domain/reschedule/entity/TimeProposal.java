package com.buddygo.buddygoserver.domain.reschedule.entity;

import com.buddygo.buddygoserver.domain.gathering.entity.Gathering;
import com.buddygo.buddygoserver.domain.reschedule.enums.TimeProposalStatus;
import com.buddygo.buddygoserver.domain.user.entity.User;
import com.buddygo.buddygoserver.global.entity.BaseCreatedEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** 시작 시각 변경 제안. 제안 시점의 구성원 모두(TimeProposalVote)가 수락하면 시작 시각이 바뀐다. */
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Entity
@Table(name = "gathering_time_proposals")
public class TimeProposal extends BaseCreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "gathering_id", nullable = false)
    private Gathering gathering;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "proposer_id", nullable = false)
    private User proposer;

    /** 제안 시점의 시작 시각 */
    @Column(name = "from_start_at", nullable = false)
    private Instant fromStartAt;

    @Column(name = "to_start_at", nullable = false)
    private Instant toStartAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private TimeProposalStatus status = TimeProposalStatus.PENDING;

    /** 이 시각부터 응답할 수 없고 만료 작업이 EXPIRED 로 바꾼다 */
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    public static TimeProposal propose(Gathering gathering, User proposer, Instant toStartAt, Instant expiresAt) {
        return TimeProposal.builder()
                .gathering(gathering)
                .proposer(proposer)
                .fromStartAt(gathering.getStartAt())
                .toStartAt(toStartAt)
                .expiresAt(expiresAt)
                .build();
    }

    public boolean isPending() {
        return status == TimeProposalStatus.PENDING;
    }

    public boolean isExpiredAt(Instant now) {
        return !now.isBefore(expiresAt);
    }

    public boolean isProposedBy(Long userId) {
        return proposer.getId().equals(userId);
    }

    public void resolve(TimeProposalStatus status, Instant now) {
        if (!isPending() || status == TimeProposalStatus.PENDING) {
            throw new IllegalStateException("진행 중인 제안만 끝낼 수 있습니다: " + id + " " + this.status + " → " + status);
        }
        this.status = status;
        this.resolvedAt = now;
    }
}
