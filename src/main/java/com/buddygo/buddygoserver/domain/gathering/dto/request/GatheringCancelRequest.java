package com.buddygo.buddygoserver.domain.gathering.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GatheringCancelRequest(
        @Schema(description = "확정 참가자에게 알림으로 전달된다.", example = "갑자기 출장이 잡혔어요. 죄송합니다.")
        @NotBlank(message = "취소 사유는 필수입니다.")
        @Size(max = 100, message = "취소 사유는 100자 이하입니다.")
        String reason
) {
}
