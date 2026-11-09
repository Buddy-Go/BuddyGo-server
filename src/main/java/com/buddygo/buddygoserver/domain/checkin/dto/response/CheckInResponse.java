package com.buddygo.buddygoserver.domain.checkin.dto.response;

import com.buddygo.buddygoserver.domain.checkin.entity.CheckIn;
import com.buddygo.buddygoserver.domain.checkin.enums.Attendance;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

public record CheckInResponse(
        @Schema(description = "ON_TIME 정상 · LATE 지각", example = "ON_TIME") Attendance attendance,
        @Schema(example = "2026-11-10T09:58:00Z") Instant checkedInAt,
        @Schema(description = "모임 장소까지의 거리(m)", example = "12") int distanceMeters
) {

    public static CheckInResponse from(CheckIn checkIn) {
        return new CheckInResponse(checkIn.getAttendance(), checkIn.getCheckedInAt(), checkIn.getDistanceM());
    }
}
