package com.buddygo.buddygoserver.domain.notification.push;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 푸시(FCM) 설정.
 *
 * @param credentialsPath Firebase 서비스 계정 JSON 파일 경로. 비어 있으면 푸시를 보내지 않는다(로컬·CI·테스트).
 * @param title           알림 제목. 본문은 앱 알림 문구 그대로다.
 * @param executor        커밋 후 푸시를 보내는 스레드 풀. 큐가 차면 그 푸시는 버린다(앱 알림은 이미 저장돼 있다).
 */
@ConfigurationProperties("push")
public record PushProperties(String credentialsPath, String title, Executor executor) {

    public PushProperties {
        if (title == null || title.isBlank()) {
            throw new IllegalStateException("push.title 이 필요합니다.");
        }
        if (executor == null || executor.poolSize() <= 0 || executor.queueCapacity() <= 0) {
            throw new IllegalStateException("push.executor.pool-size, queue-capacity 는 양수여야 합니다.");
        }
    }

    public boolean enabled() {
        return credentialsPath != null && !credentialsPath.isBlank();
    }

    public record Executor(int poolSize, int queueCapacity) {
    }
}
