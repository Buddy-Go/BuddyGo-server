package com.buddygo.buddygoserver.domain.application.dto.response;

import com.buddygo.buddygoserver.domain.application.entity.Participation;
import com.buddygo.buddygoserver.domain.application.enums.ParticipationStatus;
import com.buddygo.buddygoserver.domain.gathering.enums.GatheringStatus;
import io.swagger.v3.oas.annotations.media.Schema;

public record ApplicationResponse(
        @Schema(example = "42") Long applicationId,
        @Schema(example = "PENDING") ParticipationStatus status,
        @Schema(description = "처리 후 모임 상태. 수락으로 정원이 차면 CLOSED", example = "RECRUITING")
        GatheringStatus gatheringStatus
) {

    public static ApplicationResponse from(Participation participation) {
        return new ApplicationResponse(participation.getId(), participation.getStatus(),
                participation.getGathering().getStatus());
    }
}
