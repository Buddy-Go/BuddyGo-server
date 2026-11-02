package com.buddygo.buddygoserver.domain.application.entity;

import com.buddygo.buddygoserver.domain.application.enums.ParticipationStatus;
import com.buddygo.buddygoserver.domain.application.enums.RejectReason;
import com.buddygo.buddygoserver.domain.gathering.entity.Gathering;
import com.buddygo.buddygoserver.domain.user.entity.User;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Entity
@Table(name = "participations")
/**
 * 신청(PENDING)에서 확정(CONFIRMED)·나가기(LEFT)까지 이어지고 체크인·평가도 붙는 레코드.
 * URL 은 신청 행위를 따라 /applications 를 쓴다.
 *
 * <p>UNIQUE (gathering_id, user_id) WHERE status IN ('PENDING', 'CONFIRMED') 부분 유니크 인덱스는
 * 마이그레이션 SQL 로 추가한다.
 */
public class Participation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "gathering_id", nullable = false)
    private Gathering gathering;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ParticipationStatus status = ParticipationStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "reject_reason")
    private RejectReason rejectReason;

    @Column(length = 200)
    private String message;

    @Column(name = "applied_at", nullable = false)
    private Instant appliedAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "left_at")
    private Instant leftAt;
}
