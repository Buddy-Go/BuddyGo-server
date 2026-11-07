package com.buddygo.buddygoserver.domain.notice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record NoticeCreateRequest(
        @NotBlank @Size(max = 255) String title,
        @NotBlank @Size(max = 200) String content
) {
}
