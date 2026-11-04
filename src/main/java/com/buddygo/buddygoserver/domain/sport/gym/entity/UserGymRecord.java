package com.buddygo.buddygoserver.domain.sport.gym.entity;

import com.buddygo.buddygoserver.domain.sport.gym.enums.ArmLevel;
import com.buddygo.buddygoserver.domain.sport.gym.enums.BodyPart;
import com.buddygo.buddygoserver.domain.user.entity.User;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@EntityListeners(AuditingEntityListener.class)
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Entity
@Table(name = "user_gym_records")
public class UserGymRecord {

    @EmbeddedId
    private UserGymRecordId id;

    @MapsId("userId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    /** kg 또는 턱걸이 횟수 */
    @Column(name = "record_value")
    private BigDecimal recordValue;

    /** ARM만 사용 */
    @Enumerated(EnumType.STRING)
    @Column(name = "arm_level")
    private ArmLevel armLevel;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static UserGymRecord of(User user, BodyPart bodyPart, BigDecimal recordValue, ArmLevel armLevel) {
        return UserGymRecord.builder()
                .id(new UserGymRecordId(user.getId(), bodyPart))
                .user(user)
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
}
