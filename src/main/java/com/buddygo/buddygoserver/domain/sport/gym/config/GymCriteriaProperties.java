package com.buddygo.buddygoserver.domain.sport.gym.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

/** 헬스 기준 범위 (서비스 규칙 정의서 0장 · 8장): 중량 ±weightToleranceKg, 턱걸이 ±repsTolerance. */
@ConfigurationProperties("gym.criteria")
public record GymCriteriaProperties(BigDecimal weightToleranceKg, BigDecimal repsTolerance) {

    public GymCriteriaProperties {
        if (weightToleranceKg == null || repsTolerance == null
                || weightToleranceKg.signum() < 0 || repsTolerance.signum() < 0) {
            throw new IllegalStateException("gym.criteria.weight-tolerance-kg, reps-tolerance 는 0 이상이어야 합니다.");
        }
    }
}
