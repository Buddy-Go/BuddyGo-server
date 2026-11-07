package com.buddygo.buddygoserver.domain.notice.service;

import com.buddygo.buddygoserver.domain.notice.dto.request.CommentCreateRequest;
import com.buddygo.buddygoserver.domain.notice.dto.request.NoticeCreateRequest;
import com.buddygo.buddygoserver.domain.notice.dto.request.NoticeUpdateRequest;
import com.buddygo.buddygoserver.domain.notice.dto.response.CommentResponse;
import com.buddygo.buddygoserver.domain.notice.dto.response.NoticeResponse;
import com.buddygo.buddygoserver.domain.notice.entity.Notice;
import com.buddygo.buddygoserver.domain.notice.entity.NoticeComment;
import com.buddygo.buddygoserver.domain.notice.exception.NoticeErrorType;
import com.buddygo.buddygoserver.domain.notice.repository.NoticeCommentRepository;
import com.buddygo.buddygoserver.domain.notice.repository.NoticeRepository;
import com.buddygo.buddygoserver.domain.user.exception.UserErrorType;
import com.buddygo.buddygoserver.domain.user.repository.UserRepository;
import com.buddygo.buddygoserver.global.exception.CustomException;
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
    private final UserRepository userRepository;
    private final NoticeNotificationService notifications;
    private final Clock clock;

    public NoticeResponse create(Long userId, Long gatheringId, NoticeCreateRequest request) {
        var g = access.write(userId, gatheringId, true);
        var notice = noticeRepository.save(Notice.builder().gathering(g)
                .title(request.title().strip()).content(request.content().strip()).build());
        notifications.noticeChanged(notice, g.getHost(), false);
        return NoticeResponse.from(notice);
    }

    public NoticeResponse update(Long userId, Long gatheringId, Long noticeId, NoticeUpdateRequest request) {
        var g = access.write(userId, gatheringId, true);
        var notice = access.notice(gatheringId, noticeId);
        notice.edit(request.title(), request.content(), Instant.now(clock));
        notifications.noticeChanged(notice, g.getHost(), true);
        return NoticeResponse.from(notice);
    }

    public void delete(Long userId, Long gatheringId, Long noticeId) {
        access.write(userId, gatheringId, true);
        // DB FK가 댓글은 CASCADE 삭제하고 기존 알림의 notice_id는 SET NULL로 남긴다.
        noticeRepository.delete(access.notice(gatheringId, noticeId));
    }

    public CommentResponse comment(Long userId, Long gatheringId, Long noticeId, CommentCreateRequest request) {
        access.write(userId, gatheringId, false);
        var notice = access.notice(gatheringId, noticeId);
        var author = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(UserErrorType.NOT_FOUND));
        var comment = commentRepository.save(NoticeComment.builder().notice(notice).author(author)
                .content(request.content().strip()).build());
        notifications.commented(notice, author);
        return CommentResponse.from(comment);
    }

    public void deleteComment(Long userId, Long gatheringId, Long noticeId, Long commentId) {
        var g = access.write(userId, gatheringId, false);
        access.notice(gatheringId, noticeId);
        var comment = commentRepository.findByIdAndNoticeId(commentId, noticeId)
                .orElseThrow(() -> new CustomException(NoticeErrorType.COMMENT_NOT_FOUND));
        if (!g.isHostedBy(userId) && !comment.getAuthor().getId().equals(userId)) {
            throw new CustomException(NoticeErrorType.COMMENT_DELETE_FORBIDDEN);
        }
        commentRepository.delete(comment);
    }
}
