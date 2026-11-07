package com.buddygo.buddygoserver.support;

import com.buddygo.buddygoserver.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.Instant;
import java.time.ZoneId;

/**
 * 통합 테스트의 공통 부모. 설정을 여기 한 곳에 모아 모든 테스트가 Spring 컨텍스트 하나를 공유한다.
 * 테스트 클래스에 @SpringBootTest·@Import·중첩 @TestConfiguration 을 따로 붙이면 컨텍스트가 새로 뜨므로 붙이지 않는다.
 * 시각을 고정하려면 {@link #fixedNow()} 를 재정의한다. 시계는 테스트마다 처음 상태로 돌린다.
 * 목·스파이 빈도 컨텍스트를 가르므로 여기에만 선언한다. 스파이는 스텁하지 않으면 실제처럼 동작하고 테스트마다 초기화된다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(IntegrationTest.ClockConfig.class)
@MockitoSpyBean(types = UserRepository.class) // AuthIntegrationTest 가 사전 중복 확인을 우회해 유니크 제약 경합을 재현한다
public abstract class IntegrationTest {

    @TestConfiguration
    static class ClockConfig {
        @Bean
        @Primary
        MutableClock mutableClock(@Value("${app.time-zone}") ZoneId zone) {
            return new MutableClock(zone);
        }
    }

    @Autowired
    protected MutableClock mutableClock;

    /** 이 테스트 클래스의 고정 시각. null 이면 실제 시각을 쓴다. */
    protected Instant fixedNow() {
        return null;
    }

    @BeforeEach
    void resetClock() {
        mutableClock.fix(fixedNow());
    }
}
