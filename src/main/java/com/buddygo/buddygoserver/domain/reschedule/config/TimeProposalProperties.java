package com.buddygo.buddygoserver.domain.reschedule.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 시간 변경 제안 규칙의 설정값.
 *
 * @param maxShift 새 시작 시각은 지금 시작 시각에서 이만큼 안이어야 한다
 * @param minLead  마감 모임의 새 시작 시각은 지금부터 이만큼 뒤여야 한다. 체크인 열림보다 길어야(TimeProposalPolicy 가 검사)
 *                 통과 즉시 체크인 창에 들어가는 일이 없다
 */
@ConfigurationProperties("time-proposal")
public record TimeProposalProperties(Duration maxShift, Duration minLead) {

    public TimeProposalProperties {
        if (maxShift == null || minLead == null || !maxShift.isPositive() || !minLead.isPositive()) {
            throw new IllegalStateException("time-proposal.max-shift, min-lead 는 0보다 커야 합니다.");
        }
    }
}
