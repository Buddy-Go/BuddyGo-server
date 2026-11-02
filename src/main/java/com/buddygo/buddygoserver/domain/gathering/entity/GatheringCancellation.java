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
}
