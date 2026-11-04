package com.buddygo.buddygoserver.domain.gathering.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.time.Instant;

/** 모임 규칙의 설정값 (서비스 규칙 정의서 0장). */
@ConfigurationProperties("gathering")
public record GatheringProperties(
        Duration deadlineBeforeStart,
        Duration startTimeStep,
        int minCapacity,
        int maxCapacity,
        Duration minDuration,
        Duration maxDuration,
        Duration durationStep
) {

    public GatheringProperties {
        if (deadlineBeforeStart == null || startTimeStep == null
                || minDuration == null || maxDuration == null || durationStep == null) {
            throw new IllegalStateException("gathering.deadline-before-start, start-time-step, "
                    + "min-duration, max-duration, duration-step 설정이 필요합니다.");
        }
        if (startTimeStep.isZero() || startTimeStep.isNegative()
                || Duration.ofHours(1).toSeconds() % startTimeStep.toSeconds() != 0) {
            throw new IllegalStateException("gathering.start-time-step 은 1시간을 나누어떨어지게 하는 양수여야 합니다.");
        }
        if (minCapacity < 2 || minCapacity > maxCapacity) {
            throw new IllegalStateException("gathering.min-capacity 는 2 이상, max-capacity 이하여야 합니다.");
        }
        if (minDuration.compareTo(maxDuration) > 0 || durationStep.isZero() || durationStep.isNegative()) {
            throw new IllegalStateException("gathering 진행 시간 설정이 올바르지 않습니다.");
        }
    }

    public Instant deadlineOf(Instant startAt) {
        return startAt.minus(deadlineBeforeStart);
    }

    /**
     * 시작 시각이 단위(start-time-step)의 정각인지. 단위가 1시간의 약수라 UTC 로 나눠도 KST 기준과 같다.
     */
    public boolean isOnStartTimeStep(Instant startAt) {
        return startAt.getNano() == 0 && startAt.getEpochSecond() % startTimeStep.toSeconds() == 0;
    }

    public boolean isValidCapacity(int capacity) {
        return capacity >= minCapacity && capacity <= maxCapacity;
    }

    public boolean isValidDuration(Duration duration) {
        return duration.compareTo(minDuration) >= 0
                && duration.compareTo(maxDuration) <= 0
                && duration.toSeconds() % durationStep.toSeconds() == 0;
    }
}
