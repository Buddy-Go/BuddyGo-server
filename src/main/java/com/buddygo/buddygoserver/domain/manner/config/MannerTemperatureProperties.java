package com.buddygo.buddygoserver.domain.manner.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.util.stream.Stream;

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

    /**
     * 규칙 11장 '시스템이 자동으로 내리는 경우'. 모두 음수이고 규칙이 정한 크기 관계를 지킨다:
     * 지각은 노쇼보다 작게, 데드라인 후 나가기는 데드라인 전보다 크게, 마감 후 호스트 취소는 모집중 취소보다 크게.
     */
    public record Penalty(BigDecimal noShow, BigDecimal late,
                          BigDecimal leaveBeforeDeadline, BigDecimal leaveAfterDeadline,
                          BigDecimal hostCancelRecruiting, BigDecimal hostCancelClosed) {

        public Penalty {
            if (Stream.of(noShow, late, leaveBeforeDeadline, leaveAfterDeadline, hostCancelRecruiting, hostCancelClosed)
                    .anyMatch(v -> v == null || v.signum() >= 0)) {
                throw new IllegalStateException("manner-temperature.penalty 값은 모두 음수여야 합니다.");
            }
            if (late.compareTo(noShow) <= 0 || leaveBeforeDeadline.compareTo(leaveAfterDeadline) <= 0
                    || hostCancelRecruiting.compareTo(hostCancelClosed) <= 0) {
                throw new IllegalStateException("manner-temperature.penalty 는 no-show < late, "
                        + "leave-after-deadline < leave-before-deadline, host-cancel-closed < host-cancel-recruiting 이어야 합니다.");
            }
        }
    }
}
