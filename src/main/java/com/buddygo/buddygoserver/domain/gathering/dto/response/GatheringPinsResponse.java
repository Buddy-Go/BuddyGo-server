package com.buddygo.buddygoserver.domain.gathering.dto.response;

import com.buddygo.buddygoserver.domain.gathering.repository.GatheringSearchRepository.Pin;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record GatheringPinsResponse(List<PinResponse> pins) {

    public static GatheringPinsResponse from(List<Pin> pins) {
        return new GatheringPinsResponse(pins.stream()
                .map(p -> new PinResponse(p.parcelKey(), p.gatheringCount(), p.latitude(), p.longitude()))
                .toList());
    }

    public record PinResponse(
            @Schema(description = "핀을 묶는 지번 주소. 핀을 누르면 리스트 검색에 parcelKey 로 넘긴다", example = "서울 강남구 역삼동 123-45")
            String parcelKey,
            @Schema(description = "이 지번의 모임 수", example = "2") long gatheringCount,
            @Schema(description = "핀 좌표: 묶인 장소들의 중심점", example = "37.5009") double latitude,
            @Schema(example = "127.0364") double longitude
    ) {
    }
}
