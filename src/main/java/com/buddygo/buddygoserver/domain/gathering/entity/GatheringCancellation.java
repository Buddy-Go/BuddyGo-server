package com.buddygo.buddygoserver.domain.gathering.entity;

import com.buddygo.buddygoserver.domain.gathering.enums.CancellationType;
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
@Table(name = "gathering_cancellations")
public class GatheringCancellation {

    @Id
    @Column(name = "gathering_id")
    private Long id;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "gathering_id")
    private Gathering gathering;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CancellationType type;

    @Column(length = 100)
    private String reason;

    @Column(name = "canceled_at", nullable = false)
    private Instant canceledAt;

    /** 데드라인 후 마감된 모임에서 확정 참가자가 나가 모임 전체가 취소됨(방폭) */
    public static GatheringCancellation byMemberLeft(Gathering gathering, Instant canceledAt) {
        return GatheringCancellation.builder()
                .gathering(gathering)
                .type(CancellationType.MEMBER_LEFT)
                .canceledAt(canceledAt)
                .build();
    }

    /** 데드라인까지 정원이 차지 않아 자동 취소됨. 누구의 매너온도에도 영향이 없다. */
    public static GatheringCancellation underCapacity(Gathering gathering, Instant canceledAt) {
        return GatheringCancellation.builder()
                .gathering(gathering)
                .type(CancellationType.UNDER_CAPACITY)
                .canceledAt(canceledAt)
                .build();
    }

    public static GatheringCancellation byHost(Gathering gathering, String reason, Instant canceledAt) {
        return GatheringCancellation.builder()
                .gathering(gathering)
                .type(CancellationType.HOST)
                .reason(reason)
                .canceledAt(canceledAt)
                .build();
    }
}
