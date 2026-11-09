package com.buddygo.buddygoserver.domain.notification.service;

import com.buddygo.buddygoserver.domain.notification.entity.Notification;
import com.buddygo.buddygoserver.domain.notification.enums.NotificationType;
import com.buddygo.buddygoserver.domain.notification.repository.NotificationRepository;
import com.buddygo.buddygoserver.domain.notification.support.NotificationTarget;
import com.buddygo.buddygoserver.domain.user.entity.User;
import com.buddygo.buddygoserver.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * 앱 알림 저장. 알림을 일으킨 작업과 같은 트랜잭션에서 저장한다(작업이 롤백되면 알림도 남지 않는다).
 * 푸시 전송은 아직 없다. users.push_enabled 는 푸시 설정이라 앱 알림 목록은 설정과 무관하게 저장한다.
 *
 * <p>받는 사람 id 는 부르는 쪽이 DB 에서 읽은 값만 넘긴다. 행동한 사람(actorId)은 받는 사람에서 빠진다(자기 행동 알림 없음).
 * 이 서비스는 user 만 참조하고, 알림 대상을 정하는 일은 각 도메인이 한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class NotificationSender {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    public void send(NotificationType type, Collection<Long> receiverIds, Long actorId, NotificationTarget target,
                     String message) {
        Set<Long> receivers = receiversOf(receiverIds, actorId);
        if (receivers.isEmpty()) {
            return;
        }
        User actor = actorId == null ? null : userRepository.getReferenceById(actorId);
        notificationRepository.saveAll(receivers.stream()
                .map(id -> Notification.of(userRepository.getReferenceById(id), actor, type, target, message))
                .toList());
    }

    private static Set<Long> receiversOf(Collection<Long> receiverIds, Long actorId) {
        Set<Long> receivers = new LinkedHashSet<>();
        receiverIds.stream().filter(Objects::nonNull).forEach(receivers::add);
        if (actorId != null) {
            receivers.remove(actorId);
        }
        return receivers;
    }
}
