package com.buddygo.buddygoserver.domain.gathering.dto.response;

import com.buddygo.buddygoserver.domain.gathering.entity.Gathering;
import com.buddygo.buddygoserver.domain.gathering.entity.Place;
import com.buddygo.buddygoserver.domain.user.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

/** 탐색 카드(서비스 규칙 정의서 2장): 거리, 제목, 장소·일시, 호스트 닉네임·매너온도, 확정/정원 + 찜 여부 */
public record GatheringCardResponse(
        @Schema(example = "1") Long gatheringId,
        @Schema(example = "저녁 가슴·팔 같이 하실 분") String title,
        @Schema(example = "2026-11-10T10:00:00Z") Instant startAt,
        @Schema(example = "2026-11-10T11:30:00Z") Instant endAt,
        @Schema(description = "내 위치에서의 거리(m). 내 위치를 보내지 않았으면 null", example = "350")
        Long distanceMeters,
        PlaceResponse place,
        HostResponse host,
        @Schema(description = "확정 인원(호스트 포함)", example = "1") long confirmedCount,
        @Schema(description = "정원(호스트 포함)", example = "3") int capacity,
        @Schema(example = "false") boolean liked
) {

    public static GatheringCardResponse of(Gathering gathering, Double distanceMeters, long confirmedCount, boolean liked) {
        return new GatheringCardResponse(gathering.getId(), gathering.getTitle(), gathering.getStartAt(),
                gathering.getEndAt(), distanceMeters == null ? null : Math.round(distanceMeters),
                PlaceResponse.of(gathering.getPlace(), gathering.getDetailAddress()),
                HostResponse.of(gathering.getHost()), confirmedCount, gathering.getCapacity(), liked);
    }

    public record PlaceResponse(
            @Schema(example = "버디짐 강남점") String name,
            @Schema(example = "서울 강남구 테헤란로 123") String roadAddress,
            @Schema(example = "서울 강남구 역삼동 123-45") String jibunAddress,
            @Schema(example = "3층") String detailAddress,
            @Schema(example = "37.5009") double latitude,
            @Schema(example = "127.0364") double longitude
    ) {

        static PlaceResponse of(Place place, String detailAddress) {
            return new PlaceResponse(place.getName(), place.getRoadAddress(), place.getJibunAddress(), detailAddress,
                    place.getLocation().getY(), place.getLocation().getX());
        }
    }

    public record HostResponse(
            @Schema(example = "1") Long userId,
            @Schema(example = "헬스버디") String nickname,
            @Schema(example = "36.5") BigDecimal mannerTemperature
    ) {

        static HostResponse of(User host) {
            return new HostResponse(host.getId(), host.getNickname(), host.getMannerTemperature());
        }
    }
}
