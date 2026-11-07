package com.buddygo.buddygoserver.domain.gathering.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record GatheringSearchResponse(
        List<GatheringCardResponse> content,
        @Schema(description = "다음 카드를 불러올 때 cursor 로 보낸다. 더 없으면 null", example = "MTEwOS4yfDIwMjYtMTEtMTBUMDU6MDA6MDBafDQy")
        String nextCursor
) {
}
