package com.buddygo.buddygoserver.domain.buddy.dto.response;

import com.buddygo.buddygoserver.domain.application.entity.Participation;
import com.buddygo.buddygoserver.domain.buddy.enums.ActivityRole;
import com.buddygo.buddygoserver.domain.gathering.entity.Gathering;
import com.buddygo.buddygoserver.domain.gathering.enums.GatheringStatus;
import com.buddygo.buddygoserver.domain.sport.Sport;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/** 버디에게 보이는 활동 한 건 */
public record ActivityResponse(
        @Schema(example = "7") Long gatheringId,
        @Schema(example = "GYM") Sport sport,
        @Schema(example = "퇴근 후 등·이두 같이 치실 분") String title,
        @Schema(example = "강남 스포애니") String placeName,
        @Schema(example = "2026-11-10T10:00:00Z") Instant startAt,
        @Schema(example = "2026-11-10T11:00:00Z") Instant endAt,
        @Schema(example = "RECRUITING") GatheringStatus gatheringStatus,
        @Schema(description = "그 사람의 역할: 개설(HOST) · 확정(PARTICIPANT) · 신청 대기(APPLICANT)", example = "HOST")
        ActivityRole role,
        @Schema(description = "지금 운동 중(확정 참가자이고 시작~종료 사이)", example = "false") boolean inProgress
) {

    public static ActivityResponse of(Participation participation, Instant now) {
        Gathering gathering = participation.getGathering();
        return new ActivityResponse(gathering.getId(), gathering.getSport(), gathering.getTitle(),
                gathering.getPlace().getName(), gathering.getStartAt(), gathering.getEndAt(), gathering.getStatus(),
                roleOf(participation), isInProgress(participation, now));
    }

    /** 상태 전이가 늦어도 시각으로 판정한다. */
    public static boolean isInProgress(Participation participation, Instant now) {
        Gathering gathering = participation.getGathering();
        return participation.isConfirmed() && !now.isBefore(gathering.getStartAt()) && now.isBefore(gathering.getEndAt());
    }

    private static ActivityRole roleOf(Participation participation) {
        if (participation.getGathering().isHostedBy(participation.getUser().getId())) {
            return ActivityRole.HOST;
        }
        return participation.isConfirmed() ? ActivityRole.PARTICIPANT : ActivityRole.APPLICANT;
    }
}
