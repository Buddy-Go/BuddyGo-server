package com.buddygo.buddygoserver.domain.profile.dto.response;

import com.buddygo.buddygoserver.domain.review.dto.response.ReviewSummaryResponse.TagCountResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** 마이페이지와 다른 사용자 프로필이 같이 쓰는 활동 요약 */
public record ProfileStatsResponse(
        @Schema(description = "'후기 n': 받은 평가 수(공개하지 않는 아쉬움 평가 포함)", example = "12") long reviewCount,
        @Schema(description = "'개설 n회': 내가 연 모임 중 실제로 진행된(진행중·종료) 수", example = "4") long hostedCount,
        @Schema(description = "'참여 n회': 남이 연 모임에서 체크인(정상·지각)한 수", example = "9") long participatedCount,
        @Schema(description = "많이 받은 태그 최대 3개(아쉬움 평가의 태그 제외). 많은 순, 같으면 태그 목록 순") List<TagCountResponse> topTags
) {
}
