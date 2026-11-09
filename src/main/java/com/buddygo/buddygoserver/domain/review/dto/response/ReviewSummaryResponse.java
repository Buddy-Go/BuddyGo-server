package com.buddygo.buddygoserver.domain.review.dto.response;

import com.buddygo.buddygoserver.domain.review.enums.ReviewTagType;
import com.buddygo.buddygoserver.domain.review.enums.ReviewTagType.Polarity;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 프로필의 후기 요약.
 *
 * @param reviewCount '후기 n': 받은 평가 수. 공개하지 않는 아쉬움 평가도 센다.
 * @param topTags     많이 받은 태그. 아쉬움 평가의 태그는 세지 않는다.
 */
public record ReviewSummaryResponse(
        @Schema(description = "'후기 n': 받은 평가 수(아쉬움 포함)", example = "12") long reviewCount,
        @Schema(description = "많이 받은 태그(아쉬움 평가의 태그 제외). 많은 순, 같으면 태그 목록 순") List<TagCountResponse> topTags
) {

    public record TagCountResponse(
            @Schema(example = "PUNCTUAL") ReviewTagType code,
            @Schema(example = "시간 약속을 잘 지켜요") String label,
            @Schema(example = "POSITIVE") Polarity polarity,
            @Schema(example = "5") long count
    ) {

        public static TagCountResponse of(ReviewTagType tag, long count) {
            return new TagCountResponse(tag, tag.getLabel(), tag.getPolarity(), count);
        }
    }
}
