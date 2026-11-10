package com.buddygo.buddygoserver.domain.notice.dto.response;

import com.buddygo.buddygoserver.domain.notice.entity.Notice;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

public record NoticeResponse(Long noticeId, Long gatheringId, Long authorId, String authorNickname,
                             String title, String content, Instant createdAt, Instant editedAt, boolean edited,
                             @Schema(description = "댓글 수", example = "3") long commentCount) {
    public static NoticeResponse from(Notice notice, long commentCount) {
        var host = notice.getGathering().getHost();
        return new NoticeResponse(notice.getId(), notice.getGathering().getId(), host.getId(), host.getNickname(),
                notice.getTitle(), notice.getContent(), notice.getCreatedAt(), notice.getEditedAt(), notice.getEditedAt() != null,
                commentCount);
    }
}
