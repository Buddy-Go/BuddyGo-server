package com.buddygo.buddygoserver.domain.buddy.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

/** 보낸 항목만 바꾼다. */
public record BuddyNotificationRequest(
        @Schema(description = "이 버디가 모임을 개설하면 알림", example = "false") Boolean gatheringCreated,
        @Schema(description = "이 버디가 모임에 신청하면 알림", example = "true") Boolean gatheringApplied
) {
}
