package com.buddygo.buddygoserver.global.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** scheduler.enabled=false 면 @Scheduled 가 돌지 않는다(테스트). 스케줄러 빈은 그대로 있어 직접 호출할 수 있다. */
@Configuration
@EnableScheduling
@ConditionalOnBooleanProperty(name = "scheduler.enabled", matchIfMissing = true)
public class SchedulingConfig {
}
