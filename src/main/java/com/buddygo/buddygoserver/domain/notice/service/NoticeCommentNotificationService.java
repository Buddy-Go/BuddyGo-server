package com.buddygo.buddygoserver.domain.notice.service;

import com.buddygo.buddygoserver.domain.application.repository.ParticipationRepository;
import com.buddygo.buddygoserver.domain.notice.entity.Notice;
import com.buddygo.buddygoserver.domain.notice.entity.NoticeComment;
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
import java.util.List;
import java.util.Set;

/**
 * 댓글 저장과 같은 트랜잭션에서 앱 알림을 저장한다. 호스트는 자기 공지의 모든 댓글을, 확정 참가자는 자기가 댓글을 단
 * 공지의 새 댓글을 받는다(작성자 제외). 답글이면 원댓글 작성자는 그 대신 답글 알림을 받는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class NoticeCommentNotificationService {
    private final ParticipationRepository participationRepository;
    private final NoticeCommentRepository commentRepository;
    private final NotificationSender notificationSender;

    public void commented(Notice notice, User actor) {
        sendCommented(notice, actor, commentReceivers(notice, confirmedUserIds(notice)));
    }

    public void replied(Notice notice, NoticeComment parent, User actor) {
        List<Long> confirmed = confirmedUserIds(notice);
        Set<Long> receivers = commentReceivers(notice, confirmed);
        Long parentAuthorId = parent.getAuthor().getId();
        receivers.remove(parentAuthorId);
        sendCommented(notice, actor, receivers);
        // 모임에서 나간 원댓글 작성자는 공지를 볼 수 없으니 받지 않는다
        if (notice.getGathering().isHostedBy(parentAuthorId) || confirmed.contains(parentAuthorId)) {
            notificationSender.send(NotificationType.NOTICE_COMMENT_REPLIED, Set.of(parentAuthorId), actor.getId(),
                    target(notice), NotificationMessages.noticeCommentReplied(notice.getGathering().getTitle()));
        }
    }

    private Set<Long> commentReceivers(Notice notice, List<Long> confirmed) {
        Set<Long> commenters = new HashSet<>(commentRepository.findCommenterIds(notice.getId()));
        Set<Long> receivers = new LinkedHashSet<>();
        receivers.add(notice.getGathering().getHost().getId());
        confirmed.stream().filter(commenters::contains).forEach(receivers::add);
        return receivers;
    }

    private void sendCommented(Notice notice, User actor, Set<Long> receivers) {
        notificationSender.send(NotificationType.NOTICE_COMMENT_CREATED, receivers, actor.getId(), target(notice),
                NotificationMessages.noticeCommented(notice.getGathering().getTitle()));
    }

    private List<Long> confirmedUserIds(Notice notice) {
        return participationRepository.findConfirmedUserIds(notice.getGathering().getId());
    }

    private NotificationTarget target(Notice notice) {
        return NotificationTarget.notice(notice.getGathering().getId(), notice.getId());
    }
}
