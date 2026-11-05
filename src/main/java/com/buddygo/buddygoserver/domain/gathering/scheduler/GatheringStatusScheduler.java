package com.buddygo.buddygoserver.domain.gathering.scheduler;

import com.buddygo.buddygoserver.domain.gathering.service.GatheringStatusService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;

/**
 * 모임 상태를 시간에 맞춰 바꾼다. 트랜잭션은 모임마다 따로 열린다(이 빈은 트랜잭션 밖).
 *
 * <p>한 번 돌 때 순서가 중요하다. 서버가 멈췄다 켜져 여러 시점이 한꺼번에 지났어도 한 번에 따라잡는다:
 * 정원 미달 취소 → 진행중 → 종료. 모집중인 모임은 진행중이 되지 않고 먼저 취소된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GatheringStatusScheduler {

    private final GatheringStatusService gatheringStatusService;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${scheduler.gathering-status-delay}")
    public void run() {
        runAt(Instant.now(clock));
    }

    public void runAt(Instant now) {
        each("정원 미달 취소", gatheringStatusService.findUnderCapacityAtDeadline(now),
                id -> gatheringStatusService.cancelUnderCapacity(id, now));
        each("진행중 전환", gatheringStatusService.findStartable(now),
                id -> gatheringStatusService.start(id, now));
        each("종료 전환", gatheringStatusService.findFinishable(now),
                id -> gatheringStatusService.finish(id, now));
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
