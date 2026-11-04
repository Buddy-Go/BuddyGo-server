package com.buddygo.buddygoserver.domain.gathering.dto.request;

import com.buddygo.buddygoserver.domain.sport.gym.enums.BodyPart;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Objects;

/** 헬스 기준: 운동 부위 1개 이상 + '내 기록 표시' */
public record GymCriteriaRequest(
        @Schema(example = "[\"CHEST\", \"ARM\"]")
        @NotEmpty(message = "운동 부위를 하나 이상 골라야 합니다.")
        List<@NotNull(message = "운동 부위가 비어 있습니다.") BodyPart> parts,

        @Schema(description = "켜면 지금의 내 헬스 기록을 모임에 복사해 범위로 보여준다.", example = "true")
        @NotNull(message = "내 기록 표시 여부는 필수입니다.")
        Boolean showMyRecord
) {

    @AssertTrue(message = "같은 부위를 두 번 보낼 수 없습니다.")
    private boolean isParts() {
        if (parts == null) {
            return true;
        }
        List<BodyPart> nonNull = parts.stream().filter(Objects::nonNull).toList();
        return nonNull.size() == nonNull.stream().distinct().count();
    }
}
