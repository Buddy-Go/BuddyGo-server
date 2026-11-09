package com.buddygo.buddygoserver.domain.manner;

import com.buddygo.buddygoserver.domain.manner.config.MannerTemperatureProperties;
import com.buddygo.buddygoserver.domain.review.config.ReviewProperties;
import com.buddygo.buddygoserver.domain.review.enums.ReviewRating;
import com.buddygo.buddygoserver.domain.manner.service.MannerTemperatureCurve;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class MannerTemperatureCurveTest {

    final MannerTemperatureCurve curve =
            new MannerTemperatureCurve(new MannerTemperatureProperties(new BigDecimal("36.5"), new BigDecimal("100"),
                    new MannerTemperatureProperties.Penalty(new BigDecimal("-5"), new BigDecimal("-1"),
                            new BigDecimal("-2"), new BigDecimal("-5"), new BigDecimal("-2"), new BigDecimal("-5"))));
    final ReviewProperties.Score score = new ReviewProperties.Score(new BigDecimal("0.5"), BigDecimal.ZERO,
            new BigDecimal("-0.5"), new BigDecimal("0.1"), new BigDecimal("-0.1"));

    @ParameterizedTest(name = "{0}°C 에서 점수 {1} → {2}")
    @CsvSource({
            // 기준점에서는 점수 그대로
            "36.50, 0.5, 0.50",
            "36.50, -0.5, -0.50",
            // 오를 때: 남은 거리(100 - 현재) / 63.5 를 곱한다
            "68.25, 0.5, 0.25",
            "90.00, 0.8, 0.13",
            "99.00, 0.5, 0.01",
            // 기준점 아래에서는 덜 오르지 않는다(배율 1 로 자름)
            "20.00, 0.5, 0.50",
            // 내릴 때: 현재 / 36.5 를 곱한다
            "18.25, -0.5, -0.25",
            "1.00, -0.5, -0.01",
            // 기준점 위에서는 더 내리지 않는다(배율 1 로 자름)
            "90.00, -0.5, -0.50",
            // 점수 0 이면 변화 없음
            "50.00, 0, 0.00"
    })
    void 위로는_100에_아래로는_0에_가까울수록_변화가_줄어든다(String current, String score, String expected) {
        assertThat(curve.deltaOf(new BigDecimal(current), new BigDecimal(score))).isEqualByComparingTo(expected);
    }

    @Test
    void 아무리_좋은_평가를_받아도_100에_닿지_않고_나쁜_평가로_0_아래로_가지_않는다() {
        BigDecimal temperature = new BigDecimal("36.50");
        for (int i = 0; i < 5000; i++) {
            temperature = temperature.add(curve.deltaOf(temperature, new BigDecimal("1.5")));
        }
        assertThat(temperature).isLessThan(new BigDecimal("100"));

        for (int i = 0; i < 5000; i++) {
            temperature = temperature.add(curve.deltaOf(temperature, new BigDecimal("-1.5")));
        }
        assertThat(temperature).isGreaterThanOrEqualTo(BigDecimal.ZERO);
    }

    @Test
    void 태그_점수는_성격대로_더하고_방향은_판정이_정한다() {
        // 좋음(+0.5) + 긍정 1개(+0.1) + 부정 2개(-0.2) = +0.4
        assertThat(score.of(ReviewRating.GOOD, 1, 2)).isEqualByComparingTo("0.4");
        // 좋음 + 부정 7개 = -0.2 → 좋음은 내려가지 않아 0
        assertThat(score.of(ReviewRating.GOOD, 0, 7)).isEqualByComparingTo("0");
        // 아쉬움(-0.5) + 긍정 3개 = -0.2 → 덜 내려감
        assertThat(score.of(ReviewRating.BAD, 3, 0)).isEqualByComparingTo("-0.2");
        // 아쉬움 + 긍정 7개 = +0.2 → 아쉬움은 올라가지 않아 0
        assertThat(score.of(ReviewRating.BAD, 7, 0)).isEqualByComparingTo("0");
        // 보통(0)은 태그대로 오르내린다
        assertThat(score.of(ReviewRating.NORMAL, 0, 2)).isEqualByComparingTo("-0.2");
        assertThat(score.of(ReviewRating.NORMAL, 3, 1)).isEqualByComparingTo("0.2");
    }
}
