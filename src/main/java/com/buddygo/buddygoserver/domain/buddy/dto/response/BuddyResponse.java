package com.buddygo.buddygoserver.domain.buddy.dto.response;

import com.buddygo.buddygoserver.domain.application.entity.Participation;
import com.buddygo.buddygoserver.domain.gathering.entity.Gathering;
import com.buddygo.buddygoserver.domain.user.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

/** 버디 목록의 한 사람과 대표 활동 */
public record BuddyResponse(
        @Schema(example = "3") Long userId,
        @Schema(example = "민준") String nickname,
        @Schema(example = "41.5") BigDecimal mannerTemperature,
        @Schema(description = "대표 활동: 지금 운동 중인 모임, 없으면 가장 가까운 예정 모임. 없으면 null('예정된 모임 없어요')")
        ActivitySummary activity,
        @Schema(description = "이 버디가 모임을 개설하면 알림", example = "true") boolean notifyGatheringCreated,
        @Schema(description = "이 버디가 모임에 신청하면 알림", example = "true") boolean notifyGatheringApplied
) {

    /** 목록 한 줄에 필요한 만큼만: '지금 운동 중' 또는 '장소 · 일시' */
    public record ActivitySummary(
            @Schema(example = "7") Long gatheringId,
            @Schema(example = "강남 스포애니") String placeName,
            @Schema(example = "2026-11-10T10:00:00Z") Instant startAt,
            @Schema(description = "지금 운동 중(확정 참가자이고 시작~종료 사이)", example = "false") boolean inProgress
    ) {

        public static ActivitySummary of(Participation participation, Instant now) {
            Gathering gathering = participation.getGathering();
            return new ActivitySummary(gathering.getId(), gathering.getPlace().getName(), gathering.getStartAt(),
                    ActivityResponse.isInProgress(participation, now));
        }
    }

    public static BuddyResponse of(User buddy, ActivitySummary activity, boolean notifyGatheringCreated,
                                   boolean notifyGatheringApplied) {
        return new BuddyResponse(buddy.getId(), buddy.getNickname(), buddy.getMannerTemperature(), activity,
                notifyGatheringCreated, notifyGatheringApplied);
    }
}
