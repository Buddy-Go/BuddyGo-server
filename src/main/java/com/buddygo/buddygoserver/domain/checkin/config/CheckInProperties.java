package com.buddygo.buddygoserver.domain.checkin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.time.Instant;

/** 체크인 규칙의 설정값 (서비스 규칙 정의서 0장 · 7장). */
@ConfigurationProperties("check-in")
public record CheckInProperties(
        Duration openBeforeStart,
        Duration lateAfterStart,
        Duration noShowAfterStart,
        int radiusMeters
) {

    public CheckInProperties {
        if (openBeforeStart == null || lateAfterStart == null || noShowAfterStart == null) {
            throw new IllegalStateException("check-in.open-before-start, late-after-start, no-show-after-start 설정이 필요합니다.");
        }
        if (lateAfterStart.isNegative() || lateAfterStart.compareTo(noShowAfterStart) >= 0 || radiusMeters <= 0) {
            throw new IllegalStateException("check-in 설정이 올바르지 않습니다: 0 <= late-after-start < no-show-after-start, radius-meters > 0");
        }
    }

    /** 이 시각까지 체크인하지 않으면 노쇼다. */
    public Instant noShowAt(Instant startAt) {
        return startAt.plus(noShowAfterStart);
    }
}
