package com.buddygo.buddygoserver.domain.notice.service;

import com.buddygo.buddygoserver.domain.application.repository.ParticipationRepository;
import com.buddygo.buddygoserver.domain.notice.entity.Notice;
import com.buddygo.buddygoserver.domain.notice.repository.NoticeCommentRepository;
import com.buddygo.buddygoserver.domain.notification.entity.Notification;
import com.buddygo.buddygoserver.domain.notification.repository.NotificationRepository;
import com.buddygo.buddygoserver.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

/** 공지·댓글 저장과 같은 트랜잭션에서 앱 알림을 저장한다. 실제 푸시 전송은 공통 발송 기능 구현 후 연결한다. */
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class NoticeNotificationService {
    private final ParticipationRepository participationRepository;
    private final NoticeCommentRepository commentRepository;
    private final NotificationRepository notificationRepository;

    public void noticeChanged(Notice notice, User actor, boolean edited) {
        Map<Long, User> receivers = members(notice);
        save(notice, actor, receivers, edited ? "NOTICE_UPDATED" : "NOTICE_CREATED",
                edited ? "모임 공지가 수정되었습니다." : "새 모임 공지가 등록되었습니다.");
    }

    public void commented(Notice notice, User actor) {
        var commenters = new HashSet<>(commentRepository.findCommenterIds(notice.getId()));
        Map<Long, User> receivers = members(notice);
        receivers.keySet().removeIf(id -> !commenters.contains(id));
        User host = notice.getGathering().getHost();
        receivers.put(host.getId(), host);
        save(notice, actor, receivers, "NOTICE_COMMENT_CREATED", "모임 공지에 새 댓글이 등록되었습니다.");
    }

    private Map<Long, User> members(Notice notice) {
        Map<Long, User> users = new HashMap<>();
        participationRepository.findConfirmedWithUser(notice.getGathering().getId())
                .forEach(p -> users.put(p.getUser().getId(), p.getUser()));
        return users;
    }

    private void save(Notice notice, User actor, Map<Long, User> receivers, String type, String message) {
        receivers.remove(actor.getId());
        // pushEnabled는 푸시 설정이다. 앱 내 알림 목록은 저장하고, 푸시 연결 시 별도로 검사한다.
        notificationRepository.saveAll(receivers.values().stream().map(receiver -> Notification.builder()
                .receiver(receiver).actor(actor).type(type).gathering(notice.getGathering())
                .notice(notice).message(message).build()).toList());
    }
}
