package com.buddygo.buddygoserver.domain.gathering.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
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
public class GatheringLikeId implements Serializable {

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "gathering_id")
    private Long gatheringId;
}
