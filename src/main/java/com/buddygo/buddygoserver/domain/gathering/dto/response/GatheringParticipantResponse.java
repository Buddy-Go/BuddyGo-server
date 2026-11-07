package com.buddygo.buddygoserver.domain.gathering.dto.response;

import com.buddygo.buddygoserver.domain.application.entity.Participation;
import com.buddygo.buddygoserver.domain.user.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/** 확정 명단 조회의 한 사람. 공개 필드만 담는다(대기 신청·연락처·운동 기록 없음). */
public record GatheringParticipantResponse(
        @Schema(example = "2") Long userId,
        @Schema(example = "운동메이트") String nickname,
        @Schema(example = "36.5") BigDecimal mannerTemperature,
        @Schema(example = "false") boolean host
) {

    public static GatheringParticipantResponse of(Participation participation, Long hostId) {
        User user = participation.getUser();
        return new GatheringParticipantResponse(user.getId(), user.getNickname(), user.getMannerTemperature(),
                user.getId().equals(hostId));
    }
}
