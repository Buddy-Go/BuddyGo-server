package com.buddygo.buddygoserver.domain.notice.dto.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record NoticeUpdateRequest(
        @Size(max = 255) @Pattern(regexp = "(?s).*[^\\p{javaWhitespace}].*", message = "제목은 공백일 수 없습니다.") String title,
        @Size(max = 200) @Pattern(regexp = "(?s).*[^\\p{javaWhitespace}].*", message = "본문은 공백일 수 없습니다.") String content
) {
    @AssertTrue(message = "제목 또는 본문을 보내야 합니다.")
    public boolean isChangePresent() { return title != null || content != null; }
}
