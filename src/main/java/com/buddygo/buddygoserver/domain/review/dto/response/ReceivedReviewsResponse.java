package com.buddygo.buddygoserver.domain.review.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record ReceivedReviewsResponse(
        List<ReceivedReviewResponse> content,
        @Schema(description = "다음 후기를 불러올 때 cursor 로 보낸다. 더 없으면 null", example = "MjAyNi0xMS0xMFQwMDowMDowMFp8NDI")
        String nextCursor
) {
}
