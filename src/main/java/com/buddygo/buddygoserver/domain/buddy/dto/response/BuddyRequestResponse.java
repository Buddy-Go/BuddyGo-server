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

    /** 수락·취소처럼 신청 행을 지운 뒤 처리 결과를 돌려줄 때 */
    public static BuddyRequestResponse of(Long requestId, BuddyRequestStatus status) {
        return new BuddyRequestResponse(requestId, status);
    }
}
