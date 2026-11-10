package com.buddygo.buddygoserver.domain.notice.service;

import com.buddygo.buddygoserver.domain.notice.dto.request.CommentCreateRequest;
import com.buddygo.buddygoserver.domain.notice.dto.response.CommentResponse;
import com.buddygo.buddygoserver.domain.notice.entity.NoticeComment;
import com.buddygo.buddygoserver.domain.notice.exception.NoticeErrorType;
import com.buddygo.buddygoserver.domain.notice.repository.NoticeCommentRepository;
import com.buddygo.buddygoserver.domain.user.exception.UserErrorType;
import com.buddygo.buddygoserver.domain.user.repository.UserRepository;
import com.buddygo.buddygoserver.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@RequiredArgsConstructor
@Transactional
public class NoticeCommentCommandService {
    private final NoticeAccess access;
    private final NoticeCommentRepository commentRepository;
    private final UserRepository userRepository;
    private final NoticeCommentNotificationService notifications;

    public CommentResponse comment(Long userId, Long gatheringId, Long noticeId, CommentCreateRequest request) {
        access.write(userId, gatheringId, false);
        var notice = access.notice(gatheringId, noticeId);
        var author = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(UserErrorType.NOT_FOUND));
        var comment = commentRepository.save(NoticeComment.builder().notice(notice).author(author)
                .content(request.content().strip()).build());
        notifications.commented(notice, author);
        return CommentResponse.from(comment, notice.getGathering().getHost().getId());
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
