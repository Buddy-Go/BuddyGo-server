package com.buddygo.buddygoserver.domain.notice.service;

import com.buddygo.buddygoserver.domain.application.repository.ParticipationRepository;
import com.buddygo.buddygoserver.domain.notice.entity.Notice;
import com.buddygo.buddygoserver.domain.notice.repository.NoticeCommentRepository;
import com.buddygo.buddygoserver.domain.notification.enums.NotificationType;
import com.buddygo.buddygoserver.domain.notification.service.NotificationSender;
import com.buddygo.buddygoserver.domain.notification.support.NotificationMessages;
import com.buddygo.buddygoserver.domain.notification.support.NotificationTarget;
import com.buddygo.buddygoserver.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 댓글 저장과 같은 트랜잭션에서 앱 알림을 저장한다. 호스트는 자기 공지의 모든 댓글을, 확정 참가자는 자기가 댓글을 단
 * 공지의 새 댓글을 받는다(작성자 제외).
 */
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class NoticeCommentNotificationService {
    private final ParticipationRepository participationRepository;
    private final NoticeCommentRepository commentRepository;
    private final NotificationSender notificationSender;

    public void commented(Notice notice, User actor) {
        Long gatheringId = notice.getGathering().getId();
        Set<Long> commenters = new HashSet<>(commentRepository.findCommenterIds(notice.getId()));
        Set<Long> receivers = new LinkedHashSet<>();
        receivers.add(notice.getGathering().getHost().getId());
        participationRepository.findConfirmedUserIds(gatheringId).stream()
                .filter(commenters::contains)
                .forEach(receivers::add);
        notificationSender.send(NotificationType.NOTICE_COMMENT_CREATED, receivers, actor.getId(),
                NotificationTarget.notice(gatheringId, notice.getId()),
                NotificationMessages.noticeCommented(notice.getGathering().getTitle()));
    }
}
