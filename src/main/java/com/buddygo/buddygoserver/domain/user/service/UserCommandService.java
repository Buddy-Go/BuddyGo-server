package com.buddygo.buddygoserver.domain.user.service;

import com.buddygo.buddygoserver.domain.user.dto.response.NotificationSettingsResponse;
import com.buddygo.buddygoserver.domain.user.entity.User;
import com.buddygo.buddygoserver.domain.user.exception.UserErrorType;
import com.buddygo.buddygoserver.domain.user.repository.DeviceTokenRepository;
import com.buddygo.buddygoserver.domain.user.repository.UserRepository;
import com.buddygo.buddygoserver.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
@RequiredArgsConstructor
@Transactional
public class UserCommandService {

    private final UserRepository userRepository;
    private final DeviceTokenRepository deviceTokenRepository;
    private final Clock clock;

    public NotificationSettingsResponse changeNotificationSettings(Long userId, boolean pushEnabled) {
        User user = getUser(userId);
        user.changePushEnabled(pushEnabled);
        return new NotificationSettingsResponse(user.isPushEnabled());
    }

    public void registerFcmToken(Long userId, String fcmToken) {
        if (!userRepository.existsById(userId)) {
            throw new CustomException(UserErrorType.NOT_FOUND);
        }
        deviceTokenRepository.upsert(userId, fcmToken.strip(), Instant.now(clock));
    }

    /** 로그아웃 전에 부른다. 없거나 남의 토큰이어도 성공으로 본다(이미 이 계정의 푸시는 가지 않는다). */
    public void deleteFcmToken(Long userId, String fcmToken) {
        deviceTokenRepository.deleteByUserIdAndFcmToken(userId, fcmToken.strip());
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(UserErrorType.NOT_FOUND));
    }
}
