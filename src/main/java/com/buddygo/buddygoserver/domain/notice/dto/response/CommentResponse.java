package com.buddygo.buddygoserver.domain.notice.dto.response;

import com.buddygo.buddygoserver.domain.notice.entity.NoticeComment;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

public record CommentResponse(Long commentId, Long noticeId, Long authorId, String authorNickname,
                              @Schema(description = "작성자가 모임 호스트인지(호스트 배지)") boolean authorHost,
                              String content, Instant createdAt) {
    public static CommentResponse from(NoticeComment comment, Long hostId) {
        Long authorId = comment.getAuthor().getId();
        return new CommentResponse(comment.getId(), comment.getNotice().getId(), authorId,
                comment.getAuthor().getNickname(), authorId.equals(hostId), comment.getContent(), comment.getCreatedAt());
    }
}
