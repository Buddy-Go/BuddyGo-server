package com.buddygo.buddygoserver.domain.notification.dto.response;

import com.buddygo.buddygoserver.domain.notification.entity.Notification;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

public record NotificationResponse(
        Long notificationId,
        String type,
        String message,
        Instant createdAt,
        Instant readAt,
        boolean read,
        @Schema(description = "시스템 알림이면 null") Long actorId,
        @Schema(description = "연결이 없거나 대상 모임이 삭제되면 null") Long gatheringId,
        @Schema(description = "연결이 없거나 공지가 삭제되면 null") Long noticeId,
        @Schema(description = "연결이 없거나 버디 신청이 수락·취소되면 null(거절된 신청은 남아 있어 ID가 그대로 온다)") Long buddyRequestId
) {
    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(notification.getId(), notification.getType(), notification.getMessage(),
                notification.getCreatedAt(), notification.getReadAt(), notification.getReadAt() != null,
                notification.getActor() == null ? null : notification.getActor().getId(),
                notification.getGatheringId(), notification.getNoticeId(), notification.getBuddyRequestId());
    }
}
