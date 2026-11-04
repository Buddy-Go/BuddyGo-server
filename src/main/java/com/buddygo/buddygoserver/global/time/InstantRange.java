package com.buddygo.buddygoserver.global.time;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

/** [start, end) 반열린 구간. 쿼리는 {@code start <= t AND t < end} 로 건다. */
public record InstantRange(Instant start, Instant end) {

    public InstantRange {
        if (!start.isBefore(end)) {
            throw new IllegalArgumentException("start 는 end 보다 앞서야 합니다: " + start + " ~ " + end);
        }
    }

    /**
     * zone 기준 date 의 from 시각부터 to 시각 전까지.
     * to 가 from 보다 앞서거나 같으면(24:00 을 뜻하는 00:00, 자정을 넘는 범위) 다음 날의 to 까지로 본다.
     */
    public static InstantRange of(LocalDate date, LocalTime from, LocalTime to, ZoneId zone) {
        LocalDate endDate = to.isAfter(from) ? date : date.plusDays(1);
        return new InstantRange(date.atTime(from).atZone(zone).toInstant(),
                endDate.atTime(to).atZone(zone).toInstant());
    }

    public boolean contains(Instant instant) {
        return !instant.isBefore(start) && instant.isBefore(end);
    }
}
