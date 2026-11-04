package com.buddygo.buddygoserver.domain.sport.gym.entity;

import com.buddygo.buddygoserver.domain.application.entity.Participation;
import com.buddygo.buddygoserver.domain.sport.gym.enums.ArmLevel;
import com.buddygo.buddygoserver.domain.sport.gym.enums.BodyPart;
import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 신청 시점의 신청자 헬스 수준 스냅샷. 미설정 부위는 행이 없다. */
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Entity
@Table(name = "participation_gym_records")
public class ParticipationGymRecord {

    @EmbeddedId
    private ParticipationGymRecordId id;

    @MapsId("participationId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "participation_id")
    private Participation participation;

    /** kg 또는 턱걸이 횟수 */
    @Column(name = "record_value")
    private BigDecimal recordValue;

    /** ARM만 사용 */
    @Enumerated(EnumType.STRING)
    @Column(name = "arm_level")
    private ArmLevel armLevel;

    public static ParticipationGymRecord of(Participation participation, BodyPart bodyPart,
                                            BigDecimal recordValue, ArmLevel armLevel) {
        return ParticipationGymRecord.builder()
                .id(new ParticipationGymRecordId(participation.getId(), bodyPart))
                .participation(participation)
                .recordValue(recordValue)
                .armLevel(armLevel)
                .build();
    }

    public BodyPart getBodyPart() {
        return id.getBodyPart();
    }
}
