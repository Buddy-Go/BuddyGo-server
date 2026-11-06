package com.buddygo.buddygoserver.domain.buddy.dto.response;

import com.buddygo.buddygoserver.domain.buddy.entity.Buddy;
import io.swagger.v3.oas.annotations.media.Schema;

public record BuddyNotificationResponse(
        @Schema(example = "3") Long userId,
        @Schema(example = "false") boolean gatheringCreated,
        @Schema(example = "true") boolean gatheringApplied
) {

    public static BuddyNotificationResponse from(Buddy buddy) {
        return new BuddyNotificationResponse(buddy.getId().getBuddyId(), buddy.isNotifyGatheringCreated(),
                buddy.isNotifyGatheringApplied());
    }
}
