package com.buddygo.buddygoserver.domain.notification.service;

import com.buddygo.buddygoserver.domain.notification.entity.Notification;
import com.buddygo.buddygoserver.domain.notification.enums.NotificationType;
import com.buddygo.buddygoserver.domain.notification.push.PushEvent;
import com.buddygo.buddygoserver.domain.notification.repository.NotificationRepository;
import com.buddygo.buddygoserver.domain.notification.support.NotificationTarget;
import com.buddygo.buddygoserver.domain.user.entity.User;
import com.buddygo.buddygoserver.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * 앱 알림 저장. 알림을 일으킨 작업과 같은 트랜잭션에서 저장한다(작업이 롤백되면 알림도 남지 않는다).
 * 저장한 알림은 {@link PushEvent} 로 알려 커밋된 뒤에만 푸시로 보낸다(push 패키지). users.push_enabled 는 푸시 설정이라
 * 앱 알림 목록은 설정과 무관하게 저장한다.
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
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public void send(NotificationType type, Collection<Long> receiverIds, Long actorId, NotificationTarget target,
                     String message) {
        Set<Long> receivers = receiversOf(receiverIds, actorId);
        if (receivers.isEmpty()) {
            return;
        }
        User actor = actorId == null ? null : userRepository.getReferenceById(actorId);
        List<Notification> saved = notificationRepository.saveAll(receivers.stream()
                .map(id -> Notification.of(userRepository.getReferenceById(id), actor, type, target, message))
                .toList());
        eventPublisher.publishEvent(new PushEvent(saved.stream()
                .map(n -> new PushEvent.Item(n.getId(), n.getReceiver().getId(), type, target, n.getMessage()))
                .toList()));
    }

    /**
     * 체크인 열림처럼 상태 전이 없이 시각만으로 보내는 알림. 스케줄러가 다시 돌아도 받는 사람마다 모임당 한 번만 남도록
     * uk_notifications_once_per_gathering(V10) 충돌은 건너뛴다.
     */
    public void sendOncePerGathering(NotificationType type, Collection<Long> receiverIds, Long gatheringId,
                                     String message) {
        Instant now = Instant.now(clock);
        String fitted = Notification.fit(message);
        NotificationTarget target = NotificationTarget.gathering(gatheringId);
        List<PushEvent.Item> inserted = new ArrayList<>();
        for (Long receiverId : receiversOf(receiverIds, null)) {
            // 이미 있어 건너뛴 알림은 id 가 오지 않으므로 다시 돌아도 푸시가 중복되지 않는다
            for (Long id : notificationRepository.insertOncePerGatheringIgnoringDuplicate(receiverId, type.name(),
                    gatheringId, fitted, now)) {
                inserted.add(new PushEvent.Item(id, receiverId, type, target, fitted));
            }
        }
        if (!inserted.isEmpty()) {
            eventPublisher.publishEvent(new PushEvent(inserted));
        }
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
