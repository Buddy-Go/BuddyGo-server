package com.buddygo.buddygoserver.domain.buddy.dto.response;

import com.buddygo.buddygoserver.domain.buddy.entity.BuddyRequest;
import com.buddygo.buddygoserver.domain.user.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

public record ReceivedBuddyRequestResponse(
        @Schema(example = "12") Long requestId,
        @Schema(description = "보낸 사람", example = "3") Long userId,
        @Schema(example = "지훈") String nickname,
        @Schema(example = "42.5") BigDecimal mannerTemperature,
        @Schema(description = "참여 횟수: 남이 연 모임에서 체크인(정상·지각)한 수(내가 연 모임 제외)", example = "18") long participationCount,
        @Schema(example = "2026-11-09T12:00:00Z") Instant requestedAt
) {

    public static ReceivedBuddyRequestResponse of(BuddyRequest request, long participationCount) {
        User sender = request.getSender();
        return new ReceivedBuddyRequestResponse(request.getId(), sender.getId(), sender.getNickname(),
                sender.getMannerTemperature(), participationCount, request.getCreatedAt());
    }
}
