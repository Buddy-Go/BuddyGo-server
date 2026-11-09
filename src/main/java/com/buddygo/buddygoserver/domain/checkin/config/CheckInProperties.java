package com.buddygo.buddygoserver.domain.checkin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.buddygo.buddygoserver.domain.checkin.enums.Attendance;

import java.time.Duration;
import java.time.Instant;

/** 체크인 규칙의 설정값 (서비스 규칙 정의서 0장 · 7장). */
@ConfigurationProperties("check-in")
public record CheckInProperties(
        Duration openBeforeStart,
        Duration lateAfterStart,
        Duration noShowAfterStart,
        int radiusMeters
) {

    public CheckInProperties {
        if (openBeforeStart == null || lateAfterStart == null || noShowAfterStart == null) {
            throw new IllegalStateException("check-in.open-before-start, late-after-start, no-show-after-start 설정이 필요합니다.");
        }
        if (lateAfterStart.isNegative() || lateAfterStart.compareTo(noShowAfterStart) >= 0 || radiusMeters <= 0) {
            throw new IllegalStateException("check-in 설정이 올바르지 않습니다: 0 <= late-after-start < no-show-after-start, radius-meters > 0");
        }
    }

    /** 체크인 열림 시각 */
    public Instant openAt(Instant startAt) {
        return startAt.minus(openBeforeStart);
    }

    /** 이 시각부터 체크인하면 지각이다. */
    public Instant lateFrom(Instant startAt) {
        return startAt.plus(lateAfterStart);
    }

    /** 이 시각까지 체크인하지 않으면 노쇼다. 체크인 창은 [openAt, noShowAt) 이다. */
    public Instant noShowAt(Instant startAt) {
        return startAt.plus(noShowAfterStart);
    }

    /** 체크인 창 안의 시각을 정상·지각으로 나눈다. 창 밖인지는 부르는 쪽이 먼저 확인한다. */
    public Attendance attendanceAt(Instant startAt, Instant checkedInAt) {
        return checkedInAt.isBefore(lateFrom(startAt)) ? Attendance.ON_TIME : Attendance.LATE;
    }
}
