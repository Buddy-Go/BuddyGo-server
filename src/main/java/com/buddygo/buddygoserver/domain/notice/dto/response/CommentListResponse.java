package com.buddygo.buddygoserver.domain.notice.dto.response;

import java.util.List;

public record CommentListResponse(List<CommentResponse> content, int page, int size, boolean hasNext) {
}
