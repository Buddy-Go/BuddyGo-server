package com.buddygo.buddygoserver.domain.application.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/** 본문 없이 보내도 된다(한마디는 선택). */
public record ApplyRequest(
        @Schema(description = "호스트에게 남기는 한마디. 선택, 200자 이하", example = "근처 살아서 자주 갈 수 있어요!")
        @Size(max = 200, message = "한마디는 200자 이하입니다.")
        String message
) {
}
