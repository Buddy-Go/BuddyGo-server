package com.buddygo.buddygoserver.domain.notification.push;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 커밋된 뒤에만 푸시한다(롤백되면 보내지 않는다). FCM 호출은 요청 스레드가 아니라 푸시 전용 스레드 풀에서 한다.
 * 최선 노력이라 서버가 커밋 직후 죽거나 FCM 이 실패하면 그 푸시는 빠지지만, 앱 알림 목록에는 남아 있다.
 *
 * <p>스레드 풀은 빈으로 등록하지 않는다. Executor 빈이 하나라도 있으면 Spring Boot 가 기본 applicationTaskExecutor 를
 * 만들지 않기 때문이다. 큐가 차면 던지지 않고 버린다(커밋 후 콜백에서 예외가 나면 이미 성공한 요청이 500 이 된다).
 * 종료 때는 큐에 남은 푸시를 잠깐 기다린다.
 */
@Slf4j
@Component
public class PushEventListener implements DisposableBean {

    private final ThreadPoolTaskExecutor executor;
    private final PushService pushService;

    public PushEventListener(PushProperties properties, PushService pushService) {
        this.pushService = pushService;
        this.executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("push-");
        executor.setCorePoolSize(properties.executor().poolSize());
        executor.setMaxPoolSize(properties.executor().poolSize());
        executor.setQueueCapacity(properties.executor().queueCapacity());
        executor.setRejectedExecutionHandler((task, pool) -> log.warn("푸시 큐가 가득 차 한 건을 버립니다."));
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void afterCommit(PushEvent event) {
        executor.execute(() -> pushService.push(event.items()));
    }

    @Override
    public void destroy() {
        executor.shutdown();
    }
}
