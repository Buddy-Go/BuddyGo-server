package com.buddygo.buddygoserver.domain.notice.dto.response;

import com.buddygo.buddygoserver.domain.notice.entity.Notice;
import java.time.Instant;

public record NoticeResponse(Long noticeId, Long gatheringId, Long authorId, String authorNickname,
                             String title, String content, Instant createdAt, Instant editedAt, boolean edited) {
    public static NoticeResponse from(Notice notice) {
        var host = notice.getGathering().getHost();
        return new NoticeResponse(notice.getId(), notice.getGathering().getId(), host.getId(), host.getNickname(),
                notice.getTitle(), notice.getContent(), notice.getCreatedAt(), notice.getEditedAt(), notice.getEditedAt() != null);
    }
}
