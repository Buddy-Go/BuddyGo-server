package com.buddygo.buddygoserver.domain.notice.dto.response;

import java.util.List;

public record NoticeListResponse(List<NoticeResponse> content, int page, int size, boolean hasNext) {
}
