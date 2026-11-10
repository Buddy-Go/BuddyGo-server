package com.buddygo.buddygoserver.domain.notification.push;

import com.buddygo.buddygoserver.domain.notification.enums.NotificationType;
import com.buddygo.buddygoserver.domain.notification.support.NotificationTarget;

import java.util.List;

/** 앱 알림을 저장한 트랜잭션이 커밋되면 이 알림들을 푸시로 보낸다. NotificationSender 만 발행한다. */
public record PushEvent(List<Item> items) {

    public record Item(Long notificationId, Long receiverId, NotificationType type, NotificationTarget target,
                       String message) {
    }
}
