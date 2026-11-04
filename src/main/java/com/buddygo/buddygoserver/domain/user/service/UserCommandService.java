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

@Service
@RequiredArgsConstructor
@Transactional
public class UserCommandService {

    private final UserRepository userRepository;
    private final DeviceTokenRepository deviceTokenRepository;

    public NotificationSettingsResponse changeNotificationSettings(Long userId, boolean pushEnabled) {
        User user = getUser(userId);
        user.changePushEnabled(pushEnabled);
        return new NotificationSettingsResponse(user.isPushEnabled());
    }

    public void registerFcmToken(Long userId, String fcmToken) {
        if (!userRepository.existsById(userId)) {
            throw new CustomException(UserErrorType.NOT_FOUND);
        }
        deviceTokenRepository.upsert(userId, fcmToken.strip());
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(UserErrorType.NOT_FOUND));
    }
}
