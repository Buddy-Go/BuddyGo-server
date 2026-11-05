package com.buddygo.buddygoserver.domain.review.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.time.Instant;

/**
 * 평가 규칙의 설정값 (서비스 규칙 정의서 11장). 평가 기한은 제출 때 시각으로 판정하므로 스케줄러가 없다.
 */
@ConfigurationProperties("review")
public record ReviewProperties(Duration deadlineAfterEnd) {

    public ReviewProperties {
        if (deadlineAfterEnd == null || deadlineAfterEnd.isZero() || deadlineAfterEnd.isNegative()) {
            throw new IllegalStateException("review.deadline-after-end 는 양수여야 합니다.");
        }
    }

    /** 이 시각부터는 평가할 수 없다. */
    public Instant deadlineOf(Instant endAt) {
        return endAt.plus(deadlineAfterEnd);
    }
}
