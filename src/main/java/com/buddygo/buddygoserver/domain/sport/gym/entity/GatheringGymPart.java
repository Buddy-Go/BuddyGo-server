package com.buddygo.buddygoserver.domain.sport.gym.entity;

import com.buddygo.buddygoserver.domain.gathering.entity.Gathering;
import com.buddygo.buddygoserver.domain.sport.gym.enums.ArmLevel;
import com.buddygo.buddygoserver.domain.sport.gym.enums.BodyPart;
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

    /** 호스트 기록 스냅샷. 기록을 숨겼거나 기록이 없는 부위는 값이 모두 NULL 이다. */
    @Column(name = "record_value")
    private BigDecimal recordValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "arm_level")
    private ArmLevel armLevel;

    public static GatheringGymPart of(Gathering gathering, BodyPart bodyPart, BigDecimal recordValue, ArmLevel armLevel) {
        return GatheringGymPart.builder()
                .id(new GatheringGymPartId(gathering.getId(), bodyPart))
                .gathering(gathering)
                .recordValue(recordValue)
                .armLevel(armLevel)
                .build();
    }

    public BodyPart getBodyPart() {
        return id.getBodyPart();
    }

    public void update(BigDecimal recordValue, ArmLevel armLevel) {
        this.recordValue = recordValue;
        this.armLevel = armLevel;
    }

    /** 호스트 기록이 표시되는 부위인지 */
    public boolean hasRecord() {
        return recordValue != null || armLevel != null;
    }
}
