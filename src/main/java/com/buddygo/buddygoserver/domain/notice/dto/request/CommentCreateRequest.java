package com.buddygo.buddygoserver.domain.notice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CommentCreateRequest(@NotBlank @Size(max = 200) String content) {
}
