package com.buddygo.buddygoserver.domain.checkin.dto.response;

import com.buddygo.buddygoserver.domain.application.entity.Participation;
import com.buddygo.buddygoserver.domain.checkin.entity.CheckIn;
import com.buddygo.buddygoserver.domain.checkin.enums.Attendance;
import com.buddygo.buddygoserver.domain.user.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

public record CheckInStatusResponse(
        @Schema(description = "체크인 열림(시작 10분 전)", example = "2026-11-10T09:50:00Z") Instant openAt,
        @Schema(description = "이 시각부터는 지각(시작 후 5분)", example = "2026-11-10T10:05:00Z") Instant lateFrom,
        @Schema(description = "체크인 마감. 이때까지 체크인하지 않으면 노쇼(시작 후 10분)", example = "2026-11-10T10:10:00Z")
        Instant closeAt,
        @Schema(description = "모임 장소에서 이 거리(m) 안에서만 체크인된다", example = "50") int radiusMeters,
        @Schema(description = "내 체크인 결과") MyCheckIn me,
        @Schema(description = "확정 명단(호스트 포함)과 체크인 결과. 호스트는 항상, 확정 참가자는 마감 이후(마감·진행중·종료)에만 받고 그 외에는 null")
        List<MemberCheckIn> members
) {

    public record MyCheckIn(
            @Schema(description = "ON_TIME · LATE · NO_SHOW, 아직 체크인 기록이 없으면 null", example = "ON_TIME")
            Attendance attendance,
            @Schema(description = "노쇼거나 아직이면 null", example = "2026-11-10T09:58:00Z") Instant checkedInAt,
            @Schema(description = "체크인한 위치에서 모임 장소까지의 거리(m). 노쇼거나 아직이면 null", example = "12")
            Integer distanceMeters
    ) {

        public static MyCheckIn of(CheckIn checkIn) {
            return checkIn == null ? new MyCheckIn(null, null, null)
                    : new MyCheckIn(checkIn.getAttendance(), checkIn.getCheckedInAt(), checkIn.getDistanceM());
        }
    }

    public record MemberCheckIn(
            @Schema(example = "2") Long userId,
            @Schema(example = "운동메이트") String nickname,
            @Schema(example = "false") boolean host,
            @Schema(description = "ON_TIME · LATE · NO_SHOW, 아직 체크인 기록이 없으면 null", example = "LATE")
            Attendance attendance,
            @Schema(description = "노쇼거나 아직이면 null", example = "2026-11-10T10:06:00Z") Instant checkedInAt
    ) {

        public static MemberCheckIn of(Participation participation, Long hostId, CheckIn checkIn) {
            User user = participation.getUser();
            return new MemberCheckIn(user.getId(), user.getNickname(), user.getId().equals(hostId),
                    checkIn == null ? null : checkIn.getAttendance(),
                    checkIn == null ? null : checkIn.getCheckedInAt());
        }
    }
}
