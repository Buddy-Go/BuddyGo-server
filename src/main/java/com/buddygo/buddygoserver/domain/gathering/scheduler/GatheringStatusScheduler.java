package com.buddygo.buddygoserver.domain.gathering.scheduler;

import com.buddygo.buddygoserver.domain.checkin.config.CheckInProperties;
import com.buddygo.buddygoserver.domain.gathering.config.GatheringProperties;
import com.buddygo.buddygoserver.domain.gathering.service.GatheringStatusService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;

/**
 * 모임 상태를 시간에 맞춰 바꾼다. 트랜잭션은 모임마다 따로 열린다(이 빈은 트랜잭션 밖).
 *
 * <p>한 번 돌 때 순서가 중요하다. 서버가 멈췄다 켜져 여러 시점이 한꺼번에 지났어도 한 번에 따라잡는다:
 * 정원 미달 취소 → 진행중 → 체크인 열림 알림 → 노쇼 → 종료. 모집중인 모임은 진행중이 되지 않고 먼저 취소되고,
 * 체크인 열림 알림은 진행중이 된 모임을 건너뛰도록 진행중 전환 뒤에 돈다.
 *
 * <p>전이 시점(데드라인 = 시작 - deadline-before-start, 체크인 열림 = 시작 - open-before-start, 시작,
 * 노쇼 = 시작 + no-show-after-start,
 * 종료 = 시작 + 진행 시간)은 모두 시작 시각 단위 위에 놓이므로, 설정값들의 최대공약수 간격의 정각에만 돌면
 * 경계 시각에 늦지 않게 처리된다({@link #tickOf}). 지금 설정에서는 10분마다(:00, :10, …) 돈다.
 * 시점이 다른 작업을 추가하면 그 오프셋도 {@link #tickOf} 에 넣는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GatheringStatusScheduler implements SchedulingConfigurer {

    private final GatheringStatusService gatheringStatusService;
    private final GatheringProperties gatheringProperties;
    private final CheckInProperties checkInProperties;
    private final Clock clock;

    /**
     * 스케줄링이 켜져 있을 때만(SchedulingConfig) 불린다. 재시작·배포로 멈춰 있던 동안 지난 전이를
     * 다음 정각까지 기다리지 않도록 기동 직후에도 한 번 돈다.
     */
    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        Duration tick = tickOf(gatheringProperties, checkInProperties);
        String cron = cronOf(tick);
        log.info("모임 상태 전이 스케줄러: {} 간격 (cron \"{}\"), 기동 직후 1회", tick, cron);
        registrar.addOneTimeTask(this::run, Duration.ZERO);
        registrar.addCronTask(new CronTask(this::run, new CronTrigger(cron, clock.getZone())));
    }

    public void run() {
        runAt(Instant.now(clock));
    }

    public void runAt(Instant now) {
        each("정원 미달 취소", gatheringStatusService.findUnderCapacityAtDeadline(now),
                id -> gatheringStatusService.cancelUnderCapacity(id, now));
        each("진행중 전환", gatheringStatusService.findStartable(now),
                id -> gatheringStatusService.start(id, now));
        each("체크인 열림 알림", gatheringStatusService.findCheckInOpenable(now),
                id -> gatheringStatusService.openCheckIn(id, now));
        each("노쇼 처리", gatheringStatusService.findNoShowDue(now),
                id -> gatheringStatusService.markNoShows(id, now));
        each("종료 전환", gatheringStatusService.findFinishable(now),
                id -> gatheringStatusService.finish(id, now));
    }

    /**
     * 모든 전이 시점이 놓이는 간격: 시작 시각 단위, 진행 시간 단위, 데드라인·체크인 열림·노쇼 오프셋의 최대공약수.
     * 시작 시각 단위가 1시간의 약수라 결과도 1시간의 약수다. cron 이 분 단위라 1분 이상 정수 분이어야 한다.
     */
    static Duration tickOf(GatheringProperties gathering, CheckInProperties checkIn) {
        long seconds = List.of(gathering.startTimeStep(), gathering.durationStep(),
                        gathering.deadlineBeforeStart(), checkIn.openBeforeStart(), checkIn.noShowAfterStart()).stream()
                .map(d -> BigInteger.valueOf(d.toSeconds()))
                .reduce(BigInteger::gcd)
                .orElseThrow()
                .longValueExact();
        if (seconds < 60 || seconds % 60 != 0) {
            throw new IllegalStateException("모임 상태 전이 시점이 분 단위로 맞지 않습니다(간격 " + seconds + "초). "
                    + "gathering.start-time-step, duration-step, deadline-before-start, "
                    + "check-in.open-before-start, check-in.no-show-after-start 는 분 단위여야 합니다.");
        }
        return Duration.ofSeconds(seconds);
    }

    /** 1시간의 약수인 간격을 매시 정각 기준 cron 으로 바꾼다. 예) 10분 → 매시 :00, :10, …, :50 */
    static String cronOf(Duration tick) {
        long minutes = tick.toMinutes();
        return minutes == 60 ? "0 0 * * * *" : "0 */" + minutes + " * * * *";
    }

    private void each(String job, List<Long> gatheringIds, Consumer<Long> action) {
        for (Long gatheringId : gatheringIds) {
            try {
                action.accept(gatheringId);
            } catch (RuntimeException e) {
                log.error("모임 상태 전이 실패: job={}, gatheringId={}", job, gatheringId, e);
            }
        }
    }
}
