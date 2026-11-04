package com.buddygo.buddygoserver.domain.gathering.dto.request;

import com.buddygo.buddygoserver.domain.gathering.enums.GenderRule;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/** 정원·진행 시간·시작 시각의 허용 범위는 설정값(gathering.*)이라 서비스에서 검사한다. */
public record GatheringCreateRequest(
        @Schema(example = "저녁 가슴·팔 같이 하실 분")
        @NotBlank(message = "제목은 필수입니다.")
        @Size(max = 50, message = "제목은 50자 이하입니다.")
        String title,

        @Schema(example = "벤치 위주로 5세트씩 합니다.")
        @Size(max = 300, message = "설명은 300자 이하입니다.")
        String description,

        @Schema(description = "시작 시각(UTC). 10분 단위 정각이고 지금부터 1시간 30분 이후만 된다.", example = "2026-11-10T10:00:00Z")
        @NotNull(message = "시작 시각은 필수입니다.")
        Instant startAt,

        @Schema(description = "진행 시간(분). 30~240, 10분 단위", example = "90")
        @NotNull(message = "진행 시간은 필수입니다.")
        Integer durationMinutes,

        @Schema(description = "정원(호스트 포함) 2~3", example = "3")
        @NotNull(message = "정원은 필수입니다.")
        Integer capacity,

        @Schema(description = "호스트 성별과 다른 성별 제한은 고를 수 없다.", example = "ANY")
        @NotNull(message = "성별 조건은 필수입니다.")
        GenderRule genderRule,

        @NotNull(message = "장소는 필수입니다.")
        @Valid
        PlaceRequest place,

        @Schema(description = "상세주소(층·출입구 등)", example = "3층")
        @Size(max = 255)
        String detailAddress,

        @NotNull(message = "헬스 기준은 필수입니다.")
        @Valid
        GymCriteriaRequest gym
) {
}
