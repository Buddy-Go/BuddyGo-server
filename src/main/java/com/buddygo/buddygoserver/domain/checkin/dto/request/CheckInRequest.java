package com.buddygo.buddygoserver.domain.checkin.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/** 기기 GPS 좌표. 서버가 모임 장소와의 거리를 재서 판정한다. */
public record CheckInRequest(
        @Schema(example = "37.5009")
        @NotNull(message = "위도는 필수입니다.")
        @DecimalMin(value = "-90") @DecimalMax(value = "90")
        Double latitude,

        @Schema(example = "127.0364")
        @NotNull(message = "경도는 필수입니다.")
        @DecimalMin(value = "-180") @DecimalMax(value = "180")
        Double longitude
) {
}
