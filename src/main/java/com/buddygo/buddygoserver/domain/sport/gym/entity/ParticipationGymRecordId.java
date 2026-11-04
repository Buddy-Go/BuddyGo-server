package com.buddygo.buddygoserver.domain.sport.gym.entity;

import com.buddygo.buddygoserver.domain.sport.gym.enums.BodyPart;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.io.Serializable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Embeddable
public class ParticipationGymRecordId implements Serializable {

    @Column(name = "participation_id")
    private Long participationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "body_part")
    private BodyPart bodyPart;
}
