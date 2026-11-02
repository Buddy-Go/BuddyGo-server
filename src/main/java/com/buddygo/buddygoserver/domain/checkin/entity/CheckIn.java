package com.buddygo.buddygoserver.domain.checkin.entity;

import com.buddygo.buddygoserver.domain.application.entity.Participation;
import com.buddygo.buddygoserver.domain.checkin.enums.Attendance;
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
@Table(name = "check_ins")
public class CheckIn {

    @Id
    @Column(name = "participation_id")
    private Long id;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "participation_id")
    private Participation participation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Attendance attendance;

    @Column(name = "checked_in_at")
    private Instant checkedInAt;

    @Column(name = "distance_m")
    private Integer distanceM;
}
