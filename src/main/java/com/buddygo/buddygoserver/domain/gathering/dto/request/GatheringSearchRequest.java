package com.buddygo.buddygoserver.domain.gathering.dto.request;

import com.buddygo.buddygoserver.domain.gathering.enums.GenderRule;
import com.buddygo.buddygoserver.domain.gathering.enums.TimeSlot;
import com.buddygo.buddygoserver.domain.sport.gym.enums.ArmLevel;
import com.buddygo.buddygoserver.domain.sport.gym.enums.BodyPart;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;

/**
 * 탐색 조건(서비스 규칙 정의서 2·3장). 모임 리스트와 지도 핀이 같은 조건을 쓴다. 보내지 않은 조건은 '전체'다.
 * 검색 영역은 지도 범위(네 값 모두) 또는 구·동 중 하나다. 묶인 핀을 누른 경우에는 parcelKey 만으로도 된다.
 */
public record GatheringSearchRequest(
        @Schema(description = "지도 범위 남서쪽 위도", example = "37.49")
        @DecimalMin("-90") @DecimalMax("90") Double swLat,
        @Schema(description = "지도 범위 남서쪽 경도", example = "127.02")
        @DecimalMin("-180") @DecimalMax("180") Double swLng,
        @Schema(description = "지도 범위 북동쪽 위도", example = "37.51")
        @DecimalMin("-90") @DecimalMax("90") Double neLat,
        @Schema(description = "지도 범위 북동쪽 경도", example = "127.05")
        @DecimalMin("-180") @DecimalMax("180") Double neLng,
        @Schema(description = "구·동 위치 조건. 카카오 주소 검색의 b_code(법정동 10자리). 구·군·일반구 코드(뒤 5자리 0)는 앞 5자리, "
                + "동 코드(뒤 2자리 0)는 앞 8자리로 비교한다", example = "1168000000")
        @Pattern(regexp = "\\d{10}", message = "법정동 코드는 숫자 10자리입니다.") String regionCode,
        @Schema(description = "묶인 핀을 눌렀을 때: 그 핀의 parcelKey(지번 주소)인 모임만. 이때는 지도 범위·구·동 없이 보내도 된다", example = "서울 강남구 역삼동 123-45")
        String parcelKey,
        @Schema(description = "내 위치 위도. 경도와 함께 보내면 거리를 계산하고 거리순으로 정렬한다. 없으면 시작 시각순", example = "37.5")
        @DecimalMin("-90") @DecimalMax("90") Double lat,
        @Schema(description = "내 위치 경도", example = "127.03")
        @DecimalMin("-180") @DecimalMax("180") Double lng,
        @Schema(description = "모임 시작 날짜(KST). 시간대·시간 범위 조건은 날짜가 있어야 쓸 수 있다", example = "2026-11-10")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
        @Schema(description = "시간대(복수 선택, KST, 시작 시각 기준). fromTime·toTime 과 함께 쓸 수 없다")
        Set<TimeSlot> timeSlots,
        @Schema(description = "직접 고른 시간 범위 시작(KST, 포함)", type = "string", example = "19:00")
        @DateTimeFormat(pattern = "HH:mm") LocalTime fromTime,
        @Schema(description = "직접 고른 시간 범위 끝(KST, 미포함). fromTime 보다 앞서거나 같으면 다음 날 그 시각까지(00:00 = 24:00)",
                type = "string", example = "22:00")
        @DateTimeFormat(pattern = "HH:mm") LocalTime toTime,
        @Schema(description = "운동 부위. 고른 부위를 모두 포함하는 모임만(AND)") Set<BodyPart> parts,
        @Schema(description = "가슴(벤치프레스 1RM, kg) 범위 하한. 아래 부위별 범위를 하나라도 보내면 기준 설정이 켜진 것으로 보고, "
                + "범위를 보낸 부위 중 하나라도 모임 기준(호스트 기록)이 범위 안인 모임만 나온다. 범위를 보낸 부위는 parts 에도 있어야 한다",
                example = "70")
        @PositiveOrZero BigDecimal chestMin,
        @Schema(description = "가슴 범위 상한", example = "90") @PositiveOrZero BigDecimal chestMax,
        @Schema(description = "등(턱걸이 횟수) 범위 하한") @PositiveOrZero BigDecimal backMin,
        @Schema(description = "등 범위 상한") @PositiveOrZero BigDecimal backMax,
        @Schema(description = "하체(스쿼트 1RM, kg) 범위 하한") @PositiveOrZero BigDecimal legsMin,
        @Schema(description = "하체 범위 상한") @PositiveOrZero BigDecimal legsMax,
        @Schema(description = "어깨(오버헤드 프레스 1RM, kg) 범위 하한") @PositiveOrZero BigDecimal shoulderMin,
        @Schema(description = "어깨 범위 상한") @PositiveOrZero BigDecimal shoulderMax,
        @Schema(description = "팔 숙련도. 모임 숙련도가 같아야 범위 안") ArmLevel armLevel,
        @Schema(description = "모임 성별 조건") GenderRule gender,
        @Schema(description = "호스트 매너온도 하한(이상)", example = "50") BigDecimal minManner,
        @Schema(description = "찜한 모임만") Boolean likedOnly
) {
}
