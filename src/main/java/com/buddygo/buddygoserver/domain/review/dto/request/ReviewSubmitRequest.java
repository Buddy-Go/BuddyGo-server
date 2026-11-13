package com.buddygo.buddygoserver.domain.review.dto.request;

import com.buddygo.buddygoserver.domain.review.enums.ReviewRating;
import com.buddygo.buddygoserver.domain.review.enums.ReviewTagType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record ReviewSubmitRequest(
        @Schema(description = "평가할 사람", example = "2") @NotNull Long targetId,
        @Schema(description = "판정(필수)", example = "GOOD") @NotNull ReviewRating rating,
        @Schema(description = "태그(필수, 1~5개). 판정과 상관없이 긍정·부정 태그를 고를 수 있다", example = "[\"PUNCTUAL\", \"GOOD_SPOTTER\"]")
        @NotNull(message = "태그를 하나 이상 골라야 합니다.")
        @Size(min = 1, max = 5, message = "태그는 1개 이상 5개 이하로 골라야 합니다.")
        Set<@NotNull(message = "태그가 비어 있습니다.") ReviewTagType> tags,
        @Schema(description = "한마디(선택, 200자). 빈 문자열은 없는 것으로 본다", example = "덕분에 즐겁게 운동했어요!")
        @Size(max = 200) String comment
) {
}
