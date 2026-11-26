package com.buddygo.buddygoserver.domain.reschedule.scheduler;

import com.buddygo.buddygoserver.domain.gathering.scheduler.GatheringStatusScheduler;
import com.buddygo.buddygoserver.domain.reschedule.service.TimeProposalExpiryService;
import com.buddygo.buddygoserver.domain.reschedule.service.TimeProposalPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * 기한이 지난 시간 변경 제안을 만료시키고 알린다. 모임 상태 스케줄러와 따로 돈다(모임 도메인이 제안을 알지 않도록).
 * 같은 정각에 정원 미달 취소와 겹쳐도 둘 다 모임 행을 잠그고 다시 확인하므로 결과가 엇갈리지 않는다.
 * 응답은 기한이 지나면 막히므로 이 작업이 늦어도 기한 뒤에 통과하는 일은 없다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TimeProposalExpiryScheduler implements SchedulingConfigurer {

    private final TimeProposalExpiryService expiryService;
    private final TimeProposalPolicy policy;
    private final Clock clock;

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        Duration tick = policy.expiryTick();
        String cron = GatheringStatusScheduler.cronOf(tick);
        log.info("시간 변경 제안 만료 스케줄러: {} 간격 (cron \"{}\"), 기동 직후 1회", tick, cron);
        registrar.addOneTimeTask(this::run, Duration.ZERO);
        registrar.addCronTask(new CronTask(this::run, new CronTrigger(cron, clock.getZone())));
    }

    public void run() {
        runAt(Instant.now(clock));
    }

    public void runAt(Instant now) {
        for (Long gatheringId : expiryService.findGatheringIdsWithExpired(now)) {
            try {
                expiryService.expire(gatheringId, now);
            } catch (RuntimeException e) {
                log.error("시간 변경 제안 만료 실패: gatheringId={}", gatheringId, e);
            }
        }
    }
}
