package com.buddygo.buddygoserver.domain.sport.gym.dto.request;

import com.buddygo.buddygoserver.domain.sport.gym.enums.ArmLevel;
import com.buddygo.buddygoserver.domain.sport.gym.enums.BodyPart;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * 부위 하나의 기록. 부위에 따라 채우는 값이 다르다.
 * 가슴·하체·어깨는 recordValue(1RM kg), 등은 recordValue(턱걸이 횟수), 팔은 armLevel 만 보낸다.
 */
@ValidGymRecord
public record GymRecordRequest(
        @Schema(example = "CHEST")
        @NotNull(message = "부위는 필수입니다.")
        BodyPart bodyPart,

        @Schema(description = "가슴·하체·어깨: 1RM(kg, 정수 1~300) / 등: 턱걸이 횟수. 팔은 비운다.", example = "80")
        BigDecimal recordValue,

        @Schema(description = "팔만 사용", example = "null")
        ArmLevel armLevel
) {
}
