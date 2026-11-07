package com.buddygo.buddygoserver.domain.user.dto.response;

import com.buddygo.buddygoserver.domain.application.entity.Participation;
import com.buddygo.buddygoserver.domain.application.enums.ParticipationStatus;
import com.buddygo.buddygoserver.domain.application.enums.RejectReason;
import com.buddygo.buddygoserver.domain.checkin.enums.Attendance;
import com.buddygo.buddygoserver.domain.gathering.entity.Gathering;
import com.buddygo.buddygoserver.domain.gathering.enums.GatheringStatus;
import com.buddygo.buddygoserver.domain.sport.Sport;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

public record MyActivityResponse(
        Long gatheringId,
        Long participationId,
        String title,
        Sport sport,
        String placeName,
        String address,
        String detailAddress,
        Instant startAt,
        Instant endAt,
        Long hostId,
        String hostNickname,
        long confirmedCount,
        int capacity,
        GatheringStatus gatheringStatus,
        ParticipationStatus participationStatus,
        RejectReason rejectReason,
        @Schema(description = "체크인 판정. 아직 기록이 없으면 null") Attendance attendance,
        @Schema(description = "종료 후 정상·지각 참석은 COMPLETED, 노쇼는 NO_SHOW, 그 외 null") Result result,
        boolean inProgress
) {
    public enum Result { COMPLETED, NO_SHOW }

    public static MyActivityResponse of(Participation participation, Attendance attendance, long count, Instant now) {
        Gathering g = participation.getGathering();
        boolean ended = g.getStatus() == GatheringStatus.FINISHED || !now.isBefore(g.getEndAt());
        Result result = null;
        if (!g.isCanceled() && participation.isConfirmed()) {
            if (attendance == Attendance.NO_SHOW) {
                result = Result.NO_SHOW;
            } else if (ended && (attendance == Attendance.ON_TIME || attendance == Attendance.LATE)) {
                result = Result.COMPLETED;
            }
        }
        boolean inProgress = participation.isConfirmed() && !g.isCanceled()
                && g.getStatus() != GatheringStatus.FINISHED
                && !now.isBefore(g.getStartAt()) && now.isBefore(g.getEndAt())
                && attendance != Attendance.NO_SHOW;
        return new MyActivityResponse(g.getId(), participation.getId(), g.getTitle(), g.getSport(),
                g.getPlace().getName(), g.getPlace().getJibunAddress(), g.getDetailAddress(),
                g.getStartAt(), g.getEndAt(), g.getHost().getId(), g.getHost().getNickname(), count,
                g.getCapacity(), g.getStatus(), participation.getStatus(), participation.getRejectReason(),
                attendance, result, inProgress);
    }
}
