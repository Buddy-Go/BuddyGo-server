package com.buddygo.buddygoserver.domain.gathering.dto.request;

import com.buddygo.buddygoserver.domain.gathering.enums.GenderRule;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * 보낸 항목만 바꾼다(null = 그대로). 설명·상세주소는 빈 문자열을 보내면 지운다.
 * 확정 참가자(호스트 제외)가 있으면 제목·설명·상세주소만 바꿀 수 있다.
 */
public record GatheringUpdateRequest(
        @Schema(example = "저녁 가슴·팔 같이 하실 분")
        @Pattern(regexp = "(?s).*\\S.*", message = "제목은 비울 수 없습니다.")
        @Size(max = 50, message = "제목은 50자 이하입니다.")
        String title,

        @Size(max = 300, message = "설명은 300자 이하입니다.")
        String description,

        @Size(max = 255)
        String detailAddress,

        @Schema(description = "시작 시각(UTC). 10분 단위 정각이고, 바꾸면 지금부터 1시간 30분 이후여야 한다.")
        Instant startAt,

        @Schema(description = "진행 시간(분). 30~240, 10분 단위")
        Integer durationMinutes,

        @Schema(description = "정원(호스트 포함) 2~3")
        Integer capacity,

        GenderRule genderRule,

        @Valid
        PlaceRequest place,

        @Schema(description = "보내면 부위와 '내 기록 표시'를 통째로 바꾼다(기록도 지금 값으로 다시 복사).")
        @Valid
        GymCriteriaRequest gym
) {

    /** 확정 참가자가 있으면 잠기는 항목을 바꾸려 하는지 */
    public boolean changesCore() {
        return startAt != null || durationMinutes != null || capacity != null
                || genderRule != null || place != null || gym != null;
    }
}
