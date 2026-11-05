package com.buddygo.buddygoserver.domain.buddy.dto.response;

import com.buddygo.buddygoserver.domain.user.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/** 버디 목록의 한 사람과 대표 활동 */
public record BuddyResponse(
        @Schema(example = "3") Long userId,
        @Schema(example = "민준") String nickname,
        @Schema(example = "41.5") BigDecimal mannerTemperature,
        @Schema(description = "대표 활동: 지금 운동 중인 모임, 없으면 가장 가까운 예정 모임. 없으면 null('예정된 모임 없어요')")
        ActivityResponse activity,
        @Schema(description = "이 버디가 모임을 개설하면 알림", example = "true") boolean notifyGatheringCreated,
        @Schema(description = "이 버디가 모임에 신청하면 알림", example = "true") boolean notifyGatheringApplied
) {

    public static BuddyResponse of(User buddy, ActivityResponse activity, boolean notifyGatheringCreated,
                                   boolean notifyGatheringApplied) {
        return new BuddyResponse(buddy.getId(), buddy.getNickname(), buddy.getMannerTemperature(), activity,
                notifyGatheringCreated, notifyGatheringApplied);
    }
}
