package com.buddygo.buddygoserver.domain.user.dto.response;

import java.util.List;

public record MyActivitiesResponse(
        List<MyActivityResponse> content, int page, int size, long totalElements, int totalPages
) {
}
