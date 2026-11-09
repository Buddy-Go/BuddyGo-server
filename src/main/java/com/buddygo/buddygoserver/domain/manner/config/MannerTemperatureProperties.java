package com.buddygo.buddygoserver.domain.manner.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

/**
 * 매너온도 곡선과 시스템 페널티. neutral 은 시작값이자 기준점이고, 오를 때는 max 에, 내릴 때는 0 에 가까울수록 변화가 줄어든다.
 * neutral 은 users.manner_temperature 의 DB·엔티티 기본값(36.5)과 같아야 한다.
 * 페널티는 곡선에 넣기 전 점수다(음수).
 */
@ConfigurationProperties("manner-temperature")
public record MannerTemperatureProperties(BigDecimal neutral, BigDecimal max, Penalty penalty) {

    public MannerTemperatureProperties {
        if (neutral == null || max == null || neutral.signum() <= 0 || max.compareTo(neutral) <= 0) {
            throw new IllegalStateException("manner-temperature 는 0 < neutral < max 여야 합니다.");
        }
        if (penalty == null) {
            throw new IllegalStateException("manner-temperature.penalty 설정이 필요합니다.");
        }
    }

    /** 규칙 11장 '시스템이 자동으로 내리는 경우'. 지각은 노쇼보다 작아야 한다. */
    public record Penalty(BigDecimal noShow, BigDecimal late) {

        public Penalty {
            if (noShow == null || late == null || noShow.signum() >= 0 || late.signum() >= 0
                    || late.compareTo(noShow) <= 0) {
                throw new IllegalStateException(
                        "manner-temperature.penalty 는 no-show < late < 0 이어야 합니다(지각은 노쇼보다 작게).");
            }
        }
    }
}
