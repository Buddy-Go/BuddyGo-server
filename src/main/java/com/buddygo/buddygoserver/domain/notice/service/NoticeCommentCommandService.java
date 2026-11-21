package com.buddygo.buddygoserver.domain.notice.service;

import com.buddygo.buddygoserver.domain.notice.dto.request.CommentCreateRequest;
import com.buddygo.buddygoserver.domain.notice.dto.response.CommentResponse;
import com.buddygo.buddygoserver.domain.notice.entity.NoticeComment;
import com.buddygo.buddygoserver.domain.notice.exception.NoticeErrorType;
import com.buddygo.buddygoserver.domain.notice.repository.NoticeCommentRepository;
import com.buddygo.buddygoserver.domain.user.entity.User;
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
public class NoticeCommentCommandService {
    private final NoticeAccess access;
    private final NoticeCommentRepository commentRepository;
    private final UserRepository userRepository;
    private final NoticeCommentNotificationService notifications;
    private final Clock clock;

    public CommentResponse comment(Long userId, Long gatheringId, Long noticeId, CommentCreateRequest request) {
        access.write(userId, gatheringId, false);
        var notice = access.notice(gatheringId, noticeId);
        var author = author(userId);
        var comment = commentRepository.save(NoticeComment.builder().notice(notice).author(author)
                .content(request.content().strip()).build());
        notifications.commented(notice, author);
        return CommentResponse.from(comment, notice.getGathering().getHost().getId());
    }

    /** 답글은 1단계만. 삭제 표시된 원댓글에는 달 수 없다(없는 댓글과 같은 N002). */
    public CommentResponse reply(Long userId, Long gatheringId, Long noticeId, Long commentId, CommentCreateRequest request) {
        access.write(userId, gatheringId, false);
        var notice = access.notice(gatheringId, noticeId);
        var parent = visibleComment(noticeId, commentId);
        if (parent.isReply()) { throw new CustomException(NoticeErrorType.REPLY_TO_REPLY); }
        var author = author(userId);
        var reply = commentRepository.save(NoticeComment.replyTo(parent, author, request.content().strip()));
        notifications.replied(notice, parent, author);
        return CommentResponse.from(reply, notice.getGathering().getHost().getId());
    }

    /**
     * 답글이 달린 원댓글은 삭제 표시만 하고, 그 외는 실제로 지운다. 삭제 표시된 원댓글의 마지막 답글을 지우면 원댓글도 지운다.
     * 모임 행 잠금 뒤에 답글 유무를 읽으므로 동시에 달리는 답글과 엇갈리지 않는다.
     */
    public void deleteComment(Long userId, Long gatheringId, Long noticeId, Long commentId) {
        var g = access.write(userId, gatheringId, false);
        access.notice(gatheringId, noticeId);
        var comment = visibleComment(noticeId, commentId);
        if (!g.isHostedBy(userId) && !comment.getAuthor().getId().equals(userId)) {
            throw new CustomException(NoticeErrorType.COMMENT_DELETE_FORBIDDEN);
        }
        if (comment.isReply()) {
            var parent = comment.getParent();
            commentRepository.delete(comment);
            commentRepository.flush(); // 남은 답글 수를 세기 전에, 그리고 부모보다 먼저 지운다(자기 참조 FK)
            if (parent.isDeleted() && !commentRepository.hasReplies(parent.getId())) {
                commentRepository.delete(parent);
            }
        } else if (commentRepository.hasReplies(comment.getId())) {
            comment.markDeleted(Instant.now(clock));
        } else {
            commentRepository.delete(comment);
        }
    }

    private NoticeComment visibleComment(Long noticeId, Long commentId) {
        return commentRepository.findByIdAndNoticeId(commentId, noticeId)
                .filter(c -> !c.isDeleted())
                .orElseThrow(() -> new CustomException(NoticeErrorType.COMMENT_NOT_FOUND));
    }

    private User author(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(UserErrorType.NOT_FOUND));
    }
}
