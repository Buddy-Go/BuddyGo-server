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
