package com.buddygo.buddygoserver.domain.buddy.event;

import com.buddygo.buddygoserver.domain.application.event.ApplicationSubmittedEvent;
import com.buddygo.buddygoserver.domain.buddy.repository.BuddyRepository;
import com.buddygo.buddygoserver.domain.gathering.event.GatheringCreatedEvent;
import com.buddygo.buddygoserver.domain.notification.enums.NotificationType;
import com.buddygo.buddygoserver.domain.notification.service.NotificationSender;
import com.buddygo.buddygoserver.domain.notification.support.NotificationMessages;
import com.buddygo.buddygoserver.domain.notification.support.NotificationTarget;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 버디가 모임을 개설·신청하면, 그 버디의 알림을 켜둔 사람에게 알린다(규칙 10장 · 12장). 버디별 알림 설정은 행의 주인 기준이라
 * (user=U, buddy=X, notify_*=true) 행이 'X 의 행동을 U 에게 알린다'는 뜻이다.
 *
 * <p>동기 리스너라 개설·신청과 같은 트랜잭션에서 돈다. 알림 행만 넣고 잠금은 잡지 않는다. 대상을 버디 도메인이 정해서,
 * 모임·신청 도메인이 버디를 참조하지 않는다.
 */
@Component
@RequiredArgsConstructor
public class BuddyActivityNotificationListener {

    private final BuddyRepository buddyRepository;
    private final NotificationSender notificationSender;

    @EventListener
    public void onGatheringCreated(GatheringCreatedEvent event) {
        notificationSender.send(NotificationType.BUDDY_GATHERING_CREATED,
                buddyRepository.findUserIdsNotifiedOfCreated(event.hostId()), event.hostId(),
                NotificationTarget.gathering(event.gatheringId()),
                NotificationMessages.buddyGatheringCreated(event.hostNickname(), event.title()));
    }

    /** 신청한 모임의 호스트는 '신청 받음' 알림을 따로 받으므로 버디 알림에서 뺀다. */
    @EventListener
    public void onApplicationSubmitted(ApplicationSubmittedEvent event) {
        List<Long> receivers = buddyRepository.findUserIdsNotifiedOfApplied(event.applicantId()).stream()
                .filter(id -> !id.equals(event.hostId()))
                .toList();
        notificationSender.send(NotificationType.BUDDY_GATHERING_APPLIED, receivers, event.applicantId(),
                NotificationTarget.gathering(event.gatheringId()),
                NotificationMessages.buddyGatheringApplied(event.applicantNickname(), event.title()));
    }
}
