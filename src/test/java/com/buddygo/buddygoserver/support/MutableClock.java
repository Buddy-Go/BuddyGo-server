package com.buddygo.buddygoserver.support;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

/**
 * 모든 통합 테스트가 같은 Spring 컨텍스트를 쓰도록, 테스트마다 시각만 바꿔 끼우는 시계.
 * 고정 시각이 없으면 실제 시각을 준다. 동시성 테스트의 다른 스레드도 읽으므로 volatile 로 둔다.
 */
public class MutableClock extends Clock {

    private final ZoneId zone;
    private volatile Instant fixed;

    public MutableClock(ZoneId zone) {
        this.zone = zone;
    }

    /** null 이면 실제 시각으로 돌아간다. */
    public void fix(Instant instant) {
        this.fixed = instant;
    }

    @Override
    public Instant instant() {
        Instant now = fixed;
        return now != null ? now : Instant.now();
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return Clock.fixed(instant(), zone);
    }
}
