package com.buddygo.buddygoserver.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * 시각은 UTC Instant 로 저장·응답하고, 서비스 기준 시간대(app.time-zone)는 날짜·시간대 해석에만 쓴다.
 * 서버(JVM) 기본 시간대에 기대지 않도록 현재 시각과 기준 시간대는 이 Clock 으로만 얻는다
 * ({@code Instant.now(clock)}, {@code LocalDate.now(clock)}). 테스트는 Clock.fixed 로 바꿔 끼운다.
 */
@Configuration
public class TimeConfig {

    @Bean
    public Clock clock(@Value("${app.time-zone}") ZoneId zone) {
        return Clock.system(zone);
    }
}
