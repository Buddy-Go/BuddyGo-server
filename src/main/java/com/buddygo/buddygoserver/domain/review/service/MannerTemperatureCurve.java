package com.buddygo.buddygoserver.domain.review.service;

import com.buddygo.buddygoserver.domain.review.config.MannerTemperatureProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * 점수를 실제 온도 변화로 바꾸는 곡선. 경험치처럼 위로 갈수록 오르기 어렵다.
 * <ul>
 *   <li>오를 때: 점수 × min(1, (max - 현재) / (max - neutral)). neutral 에서는 그대로, max 에 가까울수록 0 에 가깝다.</li>
 *   <li>내릴 때: 점수 × min(1, 현재 / neutral). neutral 이상에서는 그대로, 0 에 가까울수록 0 에 가깝다.</li>
 * </ul>
 * 결과는 소수 2자리로 반올림하고 [0, max) 를 벗어나지 않게 자른다.
 */
@Component
@RequiredArgsConstructor
public class MannerTemperatureCurve {

    private static final int SCALE = 2;
    /** 저장 단위(0.01)만큼. 반올림으로 max 에 닿지 않게 그 아래에서 자른다. */
    private static final BigDecimal UNIT = BigDecimal.ONE.movePointLeft(SCALE);

    private final MannerTemperatureProperties properties;

    /** @return current 에 더할 변화량(소수 2자리). 결과 온도는 0 이상, max 미만이다. */
    public BigDecimal deltaOf(BigDecimal current, BigDecimal score) {
        BigDecimal raw = score.multiply(factor(current, score.signum()), MathContext.DECIMAL64);
        BigDecimal next = current.add(raw).setScale(SCALE, RoundingMode.HALF_UP)
                .max(BigDecimal.ZERO)
                .min(properties.max().subtract(UNIT));
        return next.subtract(current).setScale(SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal factor(BigDecimal current, int direction) {
        if (direction == 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal factor = direction > 0
                ? properties.max().subtract(current)
                        .divide(properties.max().subtract(properties.neutral()), MathContext.DECIMAL64)
                : current.divide(properties.neutral(), MathContext.DECIMAL64);
        return factor.max(BigDecimal.ZERO).min(BigDecimal.ONE);
    }
}
