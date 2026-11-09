package com.buddygo.buddygoserver.domain.notice.service;

import com.buddygo.buddygoserver.domain.application.repository.ParticipationRepository;
import com.buddygo.buddygoserver.domain.notice.entity.Notice;
import com.buddygo.buddygoserver.domain.notification.enums.NotificationType;
import com.buddygo.buddygoserver.domain.notification.service.NotificationSender;
import com.buddygo.buddygoserver.domain.notification.support.NotificationMessages;
import com.buddygo.buddygoserver.domain.notification.support.NotificationTarget;
import com.buddygo.buddygoserver.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 공지 저장과 같은 트랜잭션에서 확정 참가자에게 앱 알림을 저장한다(작성자 제외). */
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class NoticeNotificationService {
    private final ParticipationRepository participationRepository;
    private final NotificationSender notificationSender;

    public void noticeChanged(Notice notice, User actor, boolean edited) {
        Long gatheringId = notice.getGathering().getId();
        notificationSender.send(edited ? NotificationType.NOTICE_UPDATED : NotificationType.NOTICE_CREATED,
                participationRepository.findConfirmedUserIds(gatheringId), actor.getId(),
                NotificationTarget.notice(gatheringId, notice.getId()),
                edited ? NotificationMessages.noticeUpdated() : NotificationMessages.noticeCreated());
    }
}
