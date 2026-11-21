package com.buddygo.buddygoserver.domain.notice.dto.response;

import com.buddygo.buddygoserver.domain.notice.entity.NoticeComment;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

public record CommentResponse(Long commentId, Long noticeId,
                              @Schema(description = "답글이면 원댓글 ID, 원댓글이면 null") Long parentCommentId,
                              @Schema(description = "deleted면 null") Long authorId,
                              @Schema(description = "deleted면 null") String authorNickname,
                              @Schema(description = "작성자가 모임 호스트인지(호스트 배지). deleted면 false") boolean authorHost,
                              @Schema(description = "deleted면 null") String content,
                              @Schema(description = "답글이 남아 있어 '삭제된 댓글입니다'로 보여줄 원댓글") boolean deleted,
                              Instant createdAt,
                              @Schema(description = "답글(작성순, 전부). 답글 자신과 작성 응답에서는 빈 배열") List<CommentResponse> replies) {

    public static CommentResponse from(NoticeComment comment, Long hostId) {
        return from(comment, hostId, List.of());
    }

    public static CommentResponse from(NoticeComment comment, Long hostId, List<CommentResponse> replies) {
        Long parentId = comment.isReply() ? comment.getParent().getId() : null;
        if (comment.isDeleted()) {
            return new CommentResponse(comment.getId(), comment.getNotice().getId(), parentId, null, null, false,
                    null, true, comment.getCreatedAt(), replies);
        }
        Long authorId = comment.getAuthor().getId();
        return new CommentResponse(comment.getId(), comment.getNotice().getId(), parentId, authorId,
                comment.getAuthor().getNickname(), authorId.equals(hostId), comment.getContent(), false,
                comment.getCreatedAt(), replies);
    }
}
