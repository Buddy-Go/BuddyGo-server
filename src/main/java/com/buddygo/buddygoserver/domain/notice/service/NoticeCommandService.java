package com.buddygo.buddygoserver.domain.notice.service;

import com.buddygo.buddygoserver.domain.notice.dto.request.NoticeCreateRequest;
import com.buddygo.buddygoserver.domain.notice.dto.request.NoticeUpdateRequest;
import com.buddygo.buddygoserver.domain.notice.dto.response.NoticeResponse;
import com.buddygo.buddygoserver.domain.notice.entity.Notice;
import com.buddygo.buddygoserver.domain.notice.repository.NoticeCommentRepository;
import com.buddygo.buddygoserver.domain.notice.repository.NoticeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
@RequiredArgsConstructor
@Transactional
public class NoticeCommandService {
    private final NoticeAccess access;
    private final NoticeRepository noticeRepository;
    private final NoticeCommentRepository commentRepository;
    private final NoticeNotificationService notifications;
    private final Clock clock;

    public NoticeResponse create(Long userId, Long gatheringId, NoticeCreateRequest request) {
        var g = access.write(userId, gatheringId, true);
        var notice = noticeRepository.save(Notice.builder().gathering(g)
                .title(request.title().strip()).content(request.content().strip()).build());
        notifications.noticeChanged(notice, g.getHost(), false);
        return NoticeResponse.from(notice, 0);
    }

    public NoticeResponse update(Long userId, Long gatheringId, Long noticeId, NoticeUpdateRequest request) {
        var g = access.write(userId, gatheringId, true);
        var notice = access.notice(gatheringId, noticeId);
        notice.edit(request.title(), request.content(), Instant.now(clock));
        notifications.noticeChanged(notice, g.getHost(), true);
        return NoticeResponse.from(notice, commentRepository.countByNoticeId(noticeId));
    }

    public void delete(Long userId, Long gatheringId, Long noticeId) {
        access.write(userId, gatheringId, true);
        // DB FK가 댓글은 CASCADE 삭제하고 기존 알림의 notice_id는 SET NULL로 남긴다.
        noticeRepository.delete(access.notice(gatheringId, noticeId));
    }

}
