package com.buddygo.buddygoserver.domain.reschedule.service;

import com.buddygo.buddygoserver.domain.checkin.config.CheckInProperties;
import com.buddygo.buddygoserver.domain.gathering.config.GatheringProperties;
import com.buddygo.buddygoserver.domain.gathering.entity.Gathering;
import com.buddygo.buddygoserver.domain.gathering.enums.GatheringStatus;
import com.buddygo.buddygoserver.domain.gathering.scheduler.GatheringStatusScheduler;
import com.buddygo.buddygoserver.domain.reschedule.config.TimeProposalProperties;
import com.buddygo.buddygoserver.domain.reschedule.exception.TimeProposalErrorType;
import com.buddygo.buddygoserver.global.exception.CustomException;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;

/**
 * 시간 변경 제안의 시각 규칙. 응답 기한(expiresAt)이 지나기 전에만 응답할 수 있으므로, 통과 시점에도 아래가 성립한다.
 * <ul>
 *   <li>마감: 원래 체크인이 열리기 전이고, 새 시작 시각이 지금 + min-lead 이후(새 체크인도 아직 열리지 않음)</li>
 *   <li>모집중: 원래 데드라인과 새 데드라인이 모두 지나지 않음(통과하자마자 정원 미달 취소 대상이 되지 않음)</li>
 * </ul>
 */
@Component
public class TimeProposalPolicy {

    private final GatheringProperties gatheringProperties;
    private final CheckInProperties checkInProperties;
    private final TimeProposalProperties properties;

    public TimeProposalPolicy(GatheringProperties gatheringProperties, CheckInProperties checkInProperties,
                              TimeProposalProperties properties) {
        if (properties.minLead().compareTo(checkInProperties.openBeforeStart()) <= 0) {
            throw new IllegalStateException("time-proposal.min-lead 는 check-in.open-before-start 보다 길어야 합니다.");
        }
        this.gatheringProperties = gatheringProperties;
        this.checkInProperties = checkInProperties;
        this.properties = properties;
    }

    /** 제안할 수 있는 시각인지 검사한다. 모임은 모집중 또는 마감이고 시작 전이어야 한다(부르는 쪽이 먼저 확인). */
    public void validate(Gathering gathering, Instant toStartAt, Instant now) {
        Instant from = gathering.getStartAt();
        if (!gatheringProperties.isOnStartTimeStep(toStartAt)) {
            throw new CustomException(TimeProposalErrorType.INVALID_START_AT,
                    "시작 시각은 " + gatheringProperties.startTimeStep().toMinutes() + "분 단위로 정해야 합니다.");
        }
        if (toStartAt.equals(from) || Duration.between(from, toStartAt).abs().compareTo(properties.maxShift()) > 0) {
            throw new CustomException(TimeProposalErrorType.INVALID_START_AT,
                    "새 시작 시각은 지금 시작 시각과 달라야 하고 앞뒤 " + properties.maxShift().toMinutes() + "분 안이어야 합니다.");
        }
        boolean recruiting = gathering.getStatus() == GatheringStatus.RECRUITING;
        if (!now.isBefore(limitOf(from, recruiting))) {
            throw new CustomException(TimeProposalErrorType.TOO_LATE, recruiting
                    ? "모집 중인 모임은 데드라인 전까지만 시간 변경을 제안할 수 있습니다."
                    : "체크인이 열리기 전까지만 시간 변경을 제안할 수 있습니다.");
        }
        if (!now.isBefore(newStartLimitOf(toStartAt, recruiting))) {
            throw new CustomException(TimeProposalErrorType.INVALID_START_AT, recruiting
                    ? "모집 중인 모임의 새 시작 시각은 지금부터 " + gatheringProperties.deadlineBeforeStart().toMinutes()
                            + "분 이후여야 합니다."
                    : "새 시작 시각은 지금부터 " + properties.minLead().toMinutes() + "분 이후여야 합니다.");
        }
    }

    /** 응답 기한. 이때까지 모두 수락하지 않으면 만료된다. */
    public Instant expiresAt(Gathering gathering, Instant toStartAt) {
        boolean recruiting = gathering.getStatus() == GatheringStatus.RECRUITING;
        Instant a = limitOf(gathering.getStartAt(), recruiting);
        Instant b = newStartLimitOf(toStartAt, recruiting);
        return a.isBefore(b) ? a : b;
    }

    /**
     * 만료 작업 간격. 응답 기한은 시작 시각 단위 위의 시각에서 데드라인·체크인 열림·min-lead 를 뺀 값이므로
     * 모임 상태 스케줄러 간격과 min-lead 의 최대공약수 정각에 돌면 늦지 않는다.
     */
    public Duration expiryTick() {
        long seconds = BigInteger.valueOf(GatheringStatusScheduler.tickOf(gatheringProperties, checkInProperties).toSeconds())
                .gcd(BigInteger.valueOf(properties.minLead().toSeconds()))
                .longValueExact();
        if (seconds < 60 || seconds % 60 != 0) {
            throw new IllegalStateException("time-proposal.min-lead 는 분 단위여야 합니다.");
        }
        return Duration.ofSeconds(seconds);
    }

    /** 지금 시작 시각 기준 기한: 모집중은 데드라인, 마감은 체크인 열림 */
    private Instant limitOf(Instant startAt, boolean recruiting) {
        return recruiting ? gatheringProperties.deadlineOf(startAt) : checkInProperties.openAt(startAt);
    }

    /** 새 시작 시각 기준 기한: 모집중은 새 데드라인, 마감은 새 시작 - min-lead */
    private Instant newStartLimitOf(Instant toStartAt, boolean recruiting) {
        return recruiting ? gatheringProperties.deadlineOf(toStartAt) : toStartAt.minus(properties.minLead());
    }
}
