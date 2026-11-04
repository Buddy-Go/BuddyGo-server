package com.buddygo.buddygoserver.domain.gathering.enums;

import com.buddygo.buddygoserver.global.time.InstantRange;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

/** 탐색 조건의 시간대. 모임 시작 시각 기준이고 복수 선택할 수 있다. */
public enum TimeSlot {
    DAWN(LocalTime.MIDNIGHT, LocalTime.of(6, 0)),
    MORNING(LocalTime.of(6, 0), LocalTime.NOON),
    AFTERNOON(LocalTime.NOON, LocalTime.of(18, 0)),
    /** 18–24. 끝(00:00)은 다음 날 자정이다. */
    EVENING(LocalTime.of(18, 0), LocalTime.MIDNIGHT);

    private final LocalTime from;
    private final LocalTime to;

    TimeSlot(LocalTime from, LocalTime to) {
        this.from = from;
        this.to = to;
    }

    public InstantRange on(LocalDate date, ZoneId zone) {
        return InstantRange.of(date, from, to, zone);
    }
}
