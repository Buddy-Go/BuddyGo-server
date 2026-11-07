package com.buddygo.buddygoserver.domain.review.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

/**
 * 매너온도 곡선. neutral 은 시작값이자 기준점이고, 오를 때는 max 에, 내릴 때는 0 에 가까울수록 변화가 줄어든다.
 * neutral 은 users.manner_temperature 의 DB·엔티티 기본값(36.5)과 같아야 한다.
 */
@ConfigurationProperties("manner-temperature")
public record MannerTemperatureProperties(BigDecimal neutral, BigDecimal max) {

    public MannerTemperatureProperties {
        if (neutral == null || max == null || neutral.signum() <= 0 || max.compareTo(neutral) <= 0) {
            throw new IllegalStateException("manner-temperature 는 0 < neutral < max 여야 합니다.");
        }
    }
}
