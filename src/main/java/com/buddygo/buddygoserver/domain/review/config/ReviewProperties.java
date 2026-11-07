package com.buddygo.buddygoserver.domain.review.config;

import com.buddygo.buddygoserver.domain.review.enums.ReviewRating;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

/**
 * 평가 규칙의 설정값 (서비스 규칙 정의서 11장). 평가 기한은 제출 때 시각으로 판정하므로 스케줄러가 없다.
 */
@ConfigurationProperties("review")
public record ReviewProperties(Duration deadlineAfterEnd, Score score) {

    public ReviewProperties {
        if (deadlineAfterEnd == null || deadlineAfterEnd.isZero() || deadlineAfterEnd.isNegative()) {
            throw new IllegalStateException("review.deadline-after-end 는 양수여야 합니다.");
        }
        if (score == null) {
            throw new IllegalStateException("review.score 가 필요합니다.");
        }
    }

    /** 이 시각부터는 평가할 수 없다. */
    public Instant deadlineOf(Instant endAt) {
        return endAt.plus(deadlineAfterEnd);
    }

    /**
     * 평가 한 건의 점수 = 판정 기본 점수 + 긍정 태그 수 × positiveTag + 부정 태그 수 × negativeTag.
     * 태그는 판정과 상관없이 고를 수 있고 성격대로 더하지만, 방향은 판정이 정한다: 좋음은 0 아래로,
     * 아쉬움은 0 위로 가지 않는다(좋음을 줬는데 내려가는 일이 없다). 보통은 태그대로 움직인다.
     */
    public record Score(BigDecimal good, BigDecimal normal, BigDecimal bad,
                        BigDecimal positiveTag, BigDecimal negativeTag) {

        public Score {
            if (good == null || normal == null || bad == null || positiveTag == null || negativeTag == null) {
                throw new IllegalStateException("review.score.good, normal, bad, positive-tag, negative-tag 가 필요합니다.");
            }
            if (good.compareTo(normal) < 0 || normal.compareTo(bad) < 0
                    || positiveTag.signum() < 0 || negativeTag.signum() > 0) {
                throw new IllegalStateException(
                        "review.score 는 good >= normal >= bad, positive-tag >= 0, negative-tag <= 0 이어야 합니다.");
            }
        }

        public BigDecimal of(ReviewRating rating, long positiveTags, long negativeTags) {
            BigDecimal base = switch (rating) {
                case GOOD -> good;
                case NORMAL -> normal;
                case BAD -> bad;
            };
            BigDecimal score = base.add(positiveTag.multiply(BigDecimal.valueOf(positiveTags)))
                    .add(negativeTag.multiply(BigDecimal.valueOf(negativeTags)));
            return switch (rating) {
                case GOOD -> score.max(BigDecimal.ZERO);
                case NORMAL -> score;
                case BAD -> score.min(BigDecimal.ZERO);
            };
        }
    }
}
