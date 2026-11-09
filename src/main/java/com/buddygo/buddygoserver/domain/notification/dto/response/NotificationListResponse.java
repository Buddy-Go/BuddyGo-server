package com.buddygo.buddygoserver.domain.notification.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record NotificationListResponse(
        List<NotificationResponse> content, int page, int size, boolean hasNext,
        @Schema(description = "현재 페이지가 아닌 본인의 전체 미읽음 알림 수") long unreadCount
) {
}
