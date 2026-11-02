package com.buddygo.buddygoserver.domain.sport.gym.entity;

import com.buddygo.buddygoserver.domain.gathering.entity.Gathering;
import com.buddygo.buddygoserver.domain.sport.gym.enums.ArmLevel;
import jakarta.persistence.*;
import java.math.BigDecimal;
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
@Table(name = "gathering_gym_parts")
public class GatheringGymPart {

    @EmbeddedId
    private GatheringGymPartId id;

    @MapsId("gatheringId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "gathering_id")
    private Gathering gathering;

    /** 호스트 기록 스냅샷 */
    @Column(name = "record_value")
    private BigDecimal recordValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "arm_level")
    private ArmLevel armLevel;
}
