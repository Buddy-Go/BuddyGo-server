package com.buddygo.buddygoserver.domain.review.entity;

import com.buddygo.buddygoserver.domain.gathering.entity.Gathering;
import com.buddygo.buddygoserver.domain.review.enums.MannerTemperatureReason;
import com.buddygo.buddygoserver.domain.user.entity.User;
import com.buddygo.buddygoserver.global.entity.BaseCreatedEntity;
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
@Table(name = "manner_temperature_logs")
public class MannerTemperatureLog extends BaseCreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal delta;

    @Column(nullable = false)
    private String reason;

    /** 평가 외 사유 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gathering_id")
    private Gathering gathering;

    /** 평가 사유 */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "review_id", unique = true)
    private Review review;

    /** 평가 한 건이 남긴 변화. 변화량이 0 이어도 남겨 평가마다 이력이 하나씩 있게 한다. */
    public static MannerTemperatureLog ofReview(User user, BigDecimal delta, Review review) {
        return MannerTemperatureLog.builder()
                .user(user)
                .delta(delta)
                .reason(MannerTemperatureReason.REVIEW.name())
                .review(review)
                .build();
    }
}
