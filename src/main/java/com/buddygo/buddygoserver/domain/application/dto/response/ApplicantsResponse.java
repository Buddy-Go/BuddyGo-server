package com.buddygo.buddygoserver.domain.application.dto.response;

import com.buddygo.buddygoserver.domain.application.entity.Participation;
import com.buddygo.buddygoserver.domain.sport.gym.dto.response.GymProfileResponse.GymRecordResponse;
import com.buddygo.buddygoserver.domain.user.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** 참가자 관리(호스트). 확정 탭과 신청 탭을 한 번에 채운다. */
public record ApplicantsResponse(
        @Schema(description = "확정 명단(호스트 포함, 호스트가 먼저)") List<MemberResponse> confirmed,
        @Schema(description = "대기 중인 신청(먼저 신청한 순)") List<ApplicantResponse> pending
) {

    public record MemberResponse(
            @Schema(example = "1") Long userId,
            @Schema(example = "민준") String nickname,
            @Schema(example = "41.5") BigDecimal mannerTemperature,
            @Schema(example = "true") boolean host
    ) {

        public static MemberResponse of(Participation participation, Long hostId) {
            User user = participation.getUser();
            return new MemberResponse(user.getId(), user.getNickname(), user.getMannerTemperature(),
                    user.getId().equals(hostId));
        }
    }

    public record ApplicantResponse(
            @Schema(example = "42") Long applicationId,
            @Schema(example = "3") Long userId,
            @Schema(example = "서연") String nickname,
            @Schema(example = "44.1") BigDecimal mannerTemperature,
            @Schema(example = "근처 살아서 자주 갈 수 있어요.") String message,
            @Schema(example = "2026-11-09T12:00:00Z") Instant appliedAt,
            @Schema(description = "모임 부위의 신청자 헬스 기록. 기록이 없는 부위는 recorded=false('미설정')")
            List<GymRecordResponse> gymRecords
    ) {

        public static ApplicantResponse of(Participation participation, List<GymRecordResponse> gymRecords) {
            User user = participation.getUser();
            return new ApplicantResponse(participation.getId(), user.getId(), user.getNickname(),
                    user.getMannerTemperature(), participation.getMessage(), participation.getAppliedAt(), gymRecords);
        }
    }
}
