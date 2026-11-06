package com.buddygo.buddygoserver.domain.buddy.dto.response;

import com.buddygo.buddygoserver.domain.application.entity.Participation;
import com.buddygo.buddygoserver.domain.gathering.entity.Gathering;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/** 버디 프로필 '현재 활동' 카드 한 장 */
public record ActivityResponse(
        @Schema(example = "7") Long gatheringId,
        @Schema(example = "북한산 둘레길 같이 가요") String title,
        @Schema(example = "우이동 입구") String placeName,
        @Schema(example = "2026-11-10T10:00:00Z") Instant startAt,
        @Schema(description = "지금 운동 중(확정 참가자이고 시작~종료 사이)", example = "false") boolean inProgress,
        @Schema(description = "확정 인원(호스트 포함)", example = "2") long confirmedCount,
        @Schema(description = "정원(호스트 포함)", example = "3") int capacity
) {

    public static ActivityResponse of(Participation participation, Instant now, long confirmedCount) {
        Gathering gathering = participation.getGathering();
        return new ActivityResponse(gathering.getId(), gathering.getTitle(), gathering.getPlace().getName(),
                gathering.getStartAt(), isInProgress(participation, now), confirmedCount, gathering.getCapacity());
    }

    /** 상태 전이가 늦어도 시각으로 판정한다. */
    public static boolean isInProgress(Participation participation, Instant now) {
        Gathering gathering = participation.getGathering();
        return participation.isConfirmed() && !now.isBefore(gathering.getStartAt()) && now.isBefore(gathering.getEndAt());
    }
}
