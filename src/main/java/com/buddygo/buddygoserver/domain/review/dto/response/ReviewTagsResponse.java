package com.buddygo.buddygoserver.domain.review.dto.response;

import com.buddygo.buddygoserver.domain.review.enums.ReviewTagType;
import com.buddygo.buddygoserver.domain.review.enums.ReviewTagType.Polarity;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Arrays;
import java.util.List;

/** 태그 목록. 판정과 상관없이 두 묶음 모두 고를 수 있다. */
public record ReviewTagsResponse(List<TagResponse> positive, List<TagResponse> negative) {

    public static ReviewTagsResponse all() {
        return new ReviewTagsResponse(of(Polarity.POSITIVE), of(Polarity.NEGATIVE));
    }

    private static List<TagResponse> of(Polarity polarity) {
        return Arrays.stream(ReviewTagType.values())
                .filter(t -> t.getPolarity() == polarity)
                .map(t -> new TagResponse(t, t.getLabel()))
                .toList();
    }

    public record TagResponse(
            @Schema(example = "PUNCTUAL") ReviewTagType code,
            @Schema(example = "시간 약속을 잘 지켜요") String label
    ) {
    }
}
