package com.buddygo.buddygoserver.domain.notice.dto.response;

import com.buddygo.buddygoserver.domain.notice.entity.NoticeComment;
import java.time.Instant;

public record CommentResponse(Long commentId, Long noticeId, Long authorId, String authorNickname,
                              String content, Instant createdAt) {
    public static CommentResponse from(NoticeComment comment) {
        return new CommentResponse(comment.getId(), comment.getNotice().getId(), comment.getAuthor().getId(),
                comment.getAuthor().getNickname(), comment.getContent(), comment.getCreatedAt());
    }
}
