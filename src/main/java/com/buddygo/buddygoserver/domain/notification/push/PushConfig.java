package com.buddygo.buddygoserver.domain.notification.push;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
@Configuration
@EnableConfigurationProperties(PushProperties.class)
public class PushConfig {

    /** 키 파일이 있으면 FCM, 없으면 보내지 않는 클라이언트. 키 경로를 줬는데 읽을 수 없으면 부팅을 멈춘다. */
    @Bean
    PushClient pushClient(PushProperties properties) {
        if (!properties.enabled()) {
            log.info("push.credentials-path 가 없어 푸시를 보내지 않습니다.");
            return new DisabledPushClient();
        }
        try (InputStream in = Files.newInputStream(Path.of(properties.credentialsPath()))) {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(in))
                    .build();
            FirebaseApp app = FirebaseApp.getApps().stream()
                    .filter(a -> a.getName().equals(FirebaseApp.DEFAULT_APP_NAME))
                    .findFirst()
                    .orElseGet(() -> FirebaseApp.initializeApp(options));
            return new FcmPushClient(FirebaseMessaging.getInstance(app));
        } catch (IOException e) {
            throw new UncheckedIOException("Firebase 서비스 계정 키를 읽을 수 없습니다: " + properties.credentialsPath(), e);
        }
    }
}
