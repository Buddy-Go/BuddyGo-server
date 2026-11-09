package com.buddygo.buddygoserver.domain.checkin.exception;

import io.swagger.v3.oas.annotations.media.Schema;

/** C003 응답. 공통 에러 형식(code, message)에 거리와 반경을 더한다. */
public record CheckInOutOfRangeResponse(
        @Schema(example = "C003") String code,
        @Schema(example = "체크인 가능 범위를 벗어났습니다.") String message,
        @Schema(description = "현재 위치에서 모임 장소까지의 거리(m)", example = "132") int distanceMeters,
        @Schema(description = "체크인 가능 반경(m)", example = "50") int radiusMeters
) {

    public static CheckInOutOfRangeResponse from(CheckInOutOfRangeException e) {
        return new CheckInOutOfRangeResponse(e.getErrorType().getCode(), e.getMessage(),
                e.getDistanceMeters(), e.getRadiusMeters());
    }
}
