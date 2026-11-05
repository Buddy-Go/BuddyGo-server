package com.buddygo.buddygoserver.domain.buddy.dto.response;

import com.buddygo.buddygoserver.domain.buddy.entity.BuddyRequest;
import com.buddygo.buddygoserver.domain.buddy.enums.BuddyRequestStatus;
import io.swagger.v3.oas.annotations.media.Schema;

public record BuddyRequestResponse(
        @Schema(example = "12") Long requestId,
        @Schema(description = "처리 후 상태. 신청 시 상대가 보낸 신청이 있었으면 바로 ACCEPTED(버디가 됨)", example = "PENDING")
        BuddyRequestStatus status
) {

    public static BuddyRequestResponse from(BuddyRequest request) {
        return new BuddyRequestResponse(request.getId(), request.getStatus());
    }
}
