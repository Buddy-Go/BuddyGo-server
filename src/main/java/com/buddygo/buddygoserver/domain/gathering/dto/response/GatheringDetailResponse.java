package com.buddygo.buddygoserver.domain.gathering.dto.response;

import com.buddygo.buddygoserver.domain.application.entity.Participation;
import com.buddygo.buddygoserver.domain.gathering.entity.Gathering;
import com.buddygo.buddygoserver.domain.gathering.entity.GatheringCancellation;
import com.buddygo.buddygoserver.domain.gathering.entity.Place;
import com.buddygo.buddygoserver.domain.gathering.enums.CancellationType;
import com.buddygo.buddygoserver.domain.gathering.enums.GatheringStatus;
import com.buddygo.buddygoserver.domain.gathering.enums.GenderRule;
import com.buddygo.buddygoserver.domain.gathering.enums.ViewerRole;
import com.buddygo.buddygoserver.domain.sport.Sport;
import com.buddygo.buddygoserver.domain.sport.gym.dto.response.GatheringGymCriteriaResponse;
import com.buddygo.buddygoserver.domain.user.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record GatheringDetailResponse(
        @Schema(example = "1") Long gatheringId,
        @Schema(example = "GYM") Sport sport,
        @Schema(example = "저녁 가슴·팔 같이 하실 분") String title,
        String description,
        @Schema(example = "RECRUITING") GatheringStatus status,
        @Schema(example = "2026-11-10T10:00:00Z") Instant startAt,
        @Schema(example = "2026-11-10T11:30:00Z") Instant endAt,
        @Schema(example = "90") long durationMinutes,
        @Schema(description = "이때까지 정원이 안 차면 자동 취소된다.", example = "2026-11-10T08:30:00Z") Instant deadlineAt,
        @Schema(description = "정원(호스트 포함)", example = "3") int capacity,
        @Schema(description = "확정 인원(호스트 포함)", example = "1") long confirmedCount,
        @Schema(description = "대기 중인 신청 수('참가자 관리' 배지). 호스트에게만 주고 그 외에는 null", example = "2")
        Long pendingCount,
        @Schema(example = "ANY") GenderRule genderRule,
        PlaceResponse place,
        HostResponse host,
        GatheringGymCriteriaResponse gym,
        ViewerResponse viewer,
        @Schema(description = "확정 명단(호스트 포함). 호스트는 항상, 확정 참가자는 마감 이후에만 받고 그 외에는 null")
        List<MemberResponse> members,
        @Schema(description = "취소된 모임만") CancellationResponse cancellation
) {

    public static GatheringDetailResponse of(Gathering gathering, Instant deadlineAt, long confirmedCount,
                                             Long pendingCount, long hostReviewCount, GatheringGymCriteriaResponse gym, ViewerResponse viewer,
                                             List<MemberResponse> members, CancellationResponse cancellation) {
        return new GatheringDetailResponse(
                gathering.getId(), gathering.getSport(), gathering.getTitle(), gathering.getDescription(),
                gathering.getStatus(), gathering.getStartAt(), gathering.getEndAt(),
                gathering.getDuration().toMinutes(), deadlineAt, gathering.getCapacity(), confirmedCount, pendingCount,
                gathering.getGenderRule(), PlaceResponse.of(gathering.getPlace(), gathering.getDetailAddress()),
                HostResponse.of(gathering.getHost(), hostReviewCount), gym, viewer, members, cancellation);
    }

    public record PlaceResponse(
            @Schema(example = "10") Long placeId,
            @Schema(example = "27484196") String kakaoPlaceId,
            @Schema(example = "버디짐 강남점") String name,
            @Schema(example = "서울 강남구 테헤란로 123") String roadAddress,
            @Schema(example = "서울 강남구 역삼동 123-45") String jibunAddress,
            @Schema(example = "3층") String detailAddress,
            @Schema(example = "1168010100") String regionCode,
            @Schema(example = "37.5009") double latitude,
            @Schema(example = "127.0364") double longitude
    ) {

        static PlaceResponse of(Place place, String detailAddress) {
            return new PlaceResponse(place.getId(), place.getKakaoPlaceId(), place.getName(), place.getRoadAddress(),
                    place.getJibunAddress(), detailAddress, place.getRegionCode(),
                    place.getLocation().getY(), place.getLocation().getX());
        }
    }

    public record HostResponse(
            @Schema(example = "1") Long userId,
            @Schema(example = "헬스버디") String nickname,
            @Schema(example = "36.5") BigDecimal mannerTemperature,
            @Schema(description = "받은 후기 수", example = "18") long reviewCount
    ) {

        static HostResponse of(User host, long reviewCount) {
            return new HostResponse(host.getId(), host.getNickname(), host.getMannerTemperature(), reviewCount);
        }
    }

    public record ViewerResponse(
            @Schema(example = "APPLICANT") ViewerRole role,
            @Schema(description = "내 진행 중인 신청·참여 ID(대기·확정일 때)", example = "42") Long applicationId,
            @Schema(example = "false") boolean liked
    ) {
    }

    public record MemberResponse(
            @Schema(example = "2") Long userId,
            @Schema(example = "운동메이트") String nickname,
            @Schema(example = "36.5") BigDecimal mannerTemperature,
            @Schema(example = "false") boolean host
    ) {

        public static MemberResponse of(Participation participation, Long hostId) {
            User user = participation.getUser();
            return new MemberResponse(user.getId(), user.getNickname(), user.getMannerTemperature(),
                    user.getId().equals(hostId));
        }
    }

    public record CancellationResponse(
            @Schema(example = "HOST") CancellationType type,
            @Schema(description = "호스트 취소 사유. 호스트·확정 참가자에게만 보인다.") String reason,
            Instant canceledAt
    ) {

        public static CancellationResponse of(GatheringCancellation cancellation, boolean showReason) {
            return new CancellationResponse(cancellation.getType(), showReason ? cancellation.getReason() : null,
                    cancellation.getCanceledAt());
        }
    }
}
