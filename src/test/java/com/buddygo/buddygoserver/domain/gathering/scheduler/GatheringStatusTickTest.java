package com.buddygo.buddygoserver.domain.gathering.scheduler;

import com.buddygo.buddygoserver.domain.checkin.config.CheckInProperties;
import com.buddygo.buddygoserver.domain.gathering.config.GatheringProperties;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.support.CronExpression;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GatheringStatusTickTest {

    @Test
    void 지금_설정이면_10분마다_정각에_돈다() {
        Duration tick = GatheringStatusScheduler.tickOf(gathering(Duration.ofMinutes(90), Duration.ofMinutes(10)),
                checkIn(Duration.ofMinutes(10)));

        assertThat(tick).isEqualTo(Duration.ofMinutes(10));
        CronExpression cron = CronExpression.parse(GatheringStatusScheduler.cronOf(tick));
        assertThat(cron.next(LocalDateTime.of(2026, 11, 10, 9, 0, 0)))
                .isEqualTo(LocalDateTime.of(2026, 11, 10, 9, 10, 0));
        assertThat(cron.next(LocalDateTime.of(2026, 11, 10, 9, 59, 30)))
                .isEqualTo(LocalDateTime.of(2026, 11, 10, 10, 0, 0));
    }

    @Test
    void 오프셋이_단위에_안_맞으면_간격이_그만큼_줄어든다() {
        assertThat(GatheringStatusScheduler.tickOf(gathering(Duration.ofMinutes(90), Duration.ofMinutes(10)),
                checkIn(Duration.ofMinutes(15)))).isEqualTo(Duration.ofMinutes(5));
        assertThat(GatheringStatusScheduler.tickOf(gathering(Duration.ofMinutes(75), Duration.ofMinutes(10)),
                checkIn(Duration.ofMinutes(7)))).isEqualTo(Duration.ofMinutes(1));
    }

    @Test
    void 간격이_한_시간이면_매시_정각이다() {
        assertThat(GatheringStatusScheduler.cronOf(Duration.ofHours(1))).isEqualTo("0 0 * * * *");
    }

    @Test
    void 분_단위가_아니면_기동에_실패한다() {
        assertThatThrownBy(() -> GatheringStatusScheduler.tickOf(
                gathering(Duration.ofMinutes(90), Duration.ofMinutes(10)), checkIn(Duration.ofSeconds(630))))
                .isInstanceOf(IllegalStateException.class);
    }

    private GatheringProperties gathering(Duration deadlineBeforeStart, Duration startTimeStep) {
        return new GatheringProperties(deadlineBeforeStart, startTimeStep, 2, 3,
                Duration.ofMinutes(30), Duration.ofHours(4), Duration.ofMinutes(10));
    }

    private CheckInProperties checkIn(Duration noShowAfterStart) {
        return new CheckInProperties(Duration.ofMinutes(10), Duration.ofMinutes(5), noShowAfterStart, 50);
    }
}
