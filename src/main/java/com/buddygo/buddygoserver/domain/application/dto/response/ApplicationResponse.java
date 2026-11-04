package com.buddygo.buddygoserver.domain.application.dto.response;

import com.buddygo.buddygoserver.domain.application.entity.Participation;
import com.buddygo.buddygoserver.domain.application.enums.ParticipationStatus;
import io.swagger.v3.oas.annotations.media.Schema;

public record ApplicationResponse(
        @Schema(example = "42") Long applicationId,
        @Schema(example = "PENDING") ParticipationStatus status
) {

    public static ApplicationResponse from(Participation participation) {
        return new ApplicationResponse(participation.getId(), participation.getStatus());
    }
}
