package com.buddygo.buddygoserver.domain.profile.dto.response;

import com.buddygo.buddygoserver.domain.buddy.enums.BuddyRelation;
import com.buddygo.buddygoserver.domain.user.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/** 다른 사용자 프로필: 공개 정보 + 활동 요약 + 나와의 버디 관계 */
public record UserProfileResponse(
        @Schema(example = "7") Long userId,
        @Schema(example = "헬스버디") String nickname,
        @Schema(example = "36.5") BigDecimal mannerTemperature,
        @Schema(description = "내 프로필을 이 API 로 열었으면 true. 이때 버디 관계는 NONE, 신청 불가") boolean me,
        ProfileStatsResponse stats,
        BuddyStatusResponse buddy
) {

    public static UserProfileResponse of(User user, boolean me, ProfileStatsResponse stats, BuddyStatusResponse buddy) {
        return new UserProfileResponse(user.getId(), user.getNickname(), user.getMannerTemperature(), me, stats, buddy);
    }

    public record BuddyStatusResponse(
            @Schema(example = "REQUEST_SENT") BuddyRelation relation,
            @Schema(description = "대기 중인 신청이 있으면(REQUEST_SENT·REQUEST_RECEIVED) 그 신청 ID. 취소·수락·거절에 쓴다", example = "15")
            Long requestId,
            @Schema(description = "'버디 신청' 버튼을 보여줄지: 버디·신청 대기 관계가 없고 함께 운동한 적이 있을 때 true", example = "false")
            boolean canRequest
    ) {
    }
}
