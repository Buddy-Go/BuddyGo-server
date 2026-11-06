package com.buddygo.buddygoserver.domain.buddy.dto.response;

import com.buddygo.buddygoserver.domain.buddy.enums.BuddyRelation;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/** 함께 운동한 사람 한 명 */
public record WorkoutPartnerResponse(
        @Schema(example = "5") Long userId,
        @Schema(example = "서연") String nickname,
        @Schema(description = "함께 체크인한 모임 수", example = "2") long workoutCount,
        @Schema(description = "마지막으로 함께한 모임의 시작 시각", example = "2026-09-14T10:00:00Z") Instant lastWorkoutAt,
        @Schema(description = "버디 관계: NONE('메이트 신청') · REQUEST_SENT('신청함') · REQUEST_RECEIVED · BUDDY('메이트')",
                example = "NONE")
        BuddyRelation relation,
        @Schema(description = "대기 중인 신청 ID(REQUEST_SENT·REQUEST_RECEIVED 일 때만)", example = "12") Long requestId
) {
}
