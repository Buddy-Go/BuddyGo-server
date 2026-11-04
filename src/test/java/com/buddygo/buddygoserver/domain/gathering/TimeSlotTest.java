package com.buddygo.buddygoserver.domain.gathering;

import com.buddygo.buddygoserver.domain.gathering.enums.TimeSlot;
import com.buddygo.buddygoserver.global.time.InstantRange;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TimeSlotTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDate DATE = LocalDate.of(2026, 11, 5);

    @Test
    void 새벽은_KST_자정부터지만_UTC로는_전날이다() {
        InstantRange dawn = TimeSlot.DAWN.on(DATE, SEOUL);

        assertThat(dawn.start()).isEqualTo(Instant.parse("2026-11-04T15:00:00Z"));
        assertThat(dawn.end()).isEqualTo(Instant.parse("2026-11-04T21:00:00Z"));
    }

    @Test
    void KST_아침_8시_모임은_UTC_날짜가_전날이어도_오전에_걸린다() {
        Instant kst0800 = Instant.parse("2026-11-04T23:00:00Z");

        assertThat(TimeSlot.MORNING.on(DATE, SEOUL).contains(kst0800)).isTrue();
        assertThat(TimeSlot.DAWN.on(DATE, SEOUL).contains(kst0800)).isFalse();
    }

    @Test
    void 저녁은_다음_날_자정_직전까지이고_자정은_포함하지_않는다() {
        InstantRange evening = TimeSlot.EVENING.on(DATE, SEOUL);
        Instant nextMidnight = Instant.parse("2026-11-05T15:00:00Z");

        assertThat(evening.end()).isEqualTo(nextMidnight);
        assertThat(evening.contains(nextMidnight.minusSeconds(1))).isTrue();
        assertThat(evening.contains(nextMidnight)).isFalse();
        assertThat(TimeSlot.DAWN.on(DATE.plusDays(1), SEOUL).contains(nextMidnight)).isTrue();
    }

    @Test
    void 이어진_시간대의_경계는_뒤_시간대에만_속한다() {
        Instant noon = TimeSlot.AFTERNOON.on(DATE, SEOUL).start();

        assertThat(TimeSlot.MORNING.on(DATE, SEOUL).contains(noon)).isFalse();
        assertThat(TimeSlot.AFTERNOON.on(DATE, SEOUL).contains(noon)).isTrue();
    }

    @Test
    void 직접_고른_범위가_자정을_넘으면_다음_날까지로_본다() {
        InstantRange range = InstantRange.of(DATE, LocalTime.of(22, 0), LocalTime.of(2, 0), SEOUL);

        assertThat(range.start()).isEqualTo(Instant.parse("2026-11-05T13:00:00Z"));
        assertThat(range.end()).isEqualTo(Instant.parse("2026-11-05T17:00:00Z"));
    }

    @Test
    void 시작이_끝보다_늦은_구간은_만들_수_없다() {
        Instant t = Instant.parse("2026-11-05T00:00:00Z");

        assertThatThrownBy(() -> new InstantRange(t, t)).isInstanceOf(IllegalArgumentException.class);
    }
}
