package com.buddygo.buddygoserver.domain.manner.entity;

import com.buddygo.buddygoserver.domain.manner.enums.MannerTemperatureReason;
import com.buddygo.buddygoserver.domain.user.entity.User;
import com.buddygo.buddygoserver.global.entity.BaseCreatedEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 매너온도 변화 이력. 평가·모임은 엔티티 연관 없이 ID 만 둔다(DB FK 는 그대로 있다).
 * 매너온도는 평가·체크인·모임 상태 전이가 부르는 쪽이라, 연관을 두면 manner 와 그 도메인들이 서로를 참조하게 된다.
 * 이력에서 모임·평가 내용이 필요하면 ID 로 조인한다.
 */
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

    /** 평가 외 사유(노쇼·지각 등)의 모임. gatherings(id) FK */
    @Column(name = "gathering_id")
    private Long gatheringId;

    /** 평가 사유의 평가. reviews(id) FK, 평가 하나에 이력 하나(uk_manner_temperature_logs_review) */
    @Column(name = "review_id", unique = true)
    private Long reviewId;

    /** 평가 한 건이 남긴 변화. 변화량이 0 이어도 남겨 평가마다 이력이 하나씩 있게 한다. */
    public static MannerTemperatureLog ofReview(User user, BigDecimal delta, Long reviewId) {
        return MannerTemperatureLog.builder()
                .user(user)
                .delta(delta)
                .reason(MannerTemperatureReason.REVIEW.name())
                .reviewId(reviewId)
                .build();
    }

    /** 모임에 걸린 시스템 변경 중 모임당 여러 번 생길 수 있는 것(데드라인 전 나가기). 나머지는 저장소의 ON CONFLICT INSERT 로 넣는다. */
    public static MannerTemperatureLog ofGathering(User user, BigDecimal delta, MannerTemperatureReason reason,
                                                   Long gatheringId) {
        return MannerTemperatureLog.builder()
                .user(user)
                .delta(delta)
                .reason(reason.name())
                .gatheringId(gatheringId)
                .build();
    }
}
