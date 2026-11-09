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

    /** GPS 체크인 성공(정상·지각). 노쇼는 스케줄러가 네이티브 INSERT 로 남긴다. */
    public static CheckIn attended(Participation participation, Attendance attendance, Instant checkedInAt,
                                   int distanceM) {
        return CheckIn.builder()
                .participation(participation)
                .attendance(attendance)
                .checkedInAt(checkedInAt)
                .distanceM(distanceM)
                .build();
    }
}
