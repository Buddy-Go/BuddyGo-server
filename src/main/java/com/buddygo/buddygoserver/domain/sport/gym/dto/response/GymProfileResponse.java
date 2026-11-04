package com.buddygo.buddygoserver.domain.sport.gym.dto.response;

import com.buddygo.buddygoserver.domain.sport.gym.entity.UserGymRecord;
import com.buddygo.buddygoserver.domain.sport.gym.enums.ArmLevel;
import com.buddygo.buddygoserver.domain.sport.gym.enums.BodyPart;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 헬스 기록. 다섯 부위를 항상 순서대로 담고, 미설정 부위는 recorded=false 에 값이 null 이다. */
public record GymProfileResponse(List<GymRecordResponse> records) {

    public static GymProfileResponse from(Collection<UserGymRecord> saved) {
        Map<BodyPart, UserGymRecord> byPart = saved.stream()
                .collect(Collectors.toMap(UserGymRecord::getBodyPart, Function.identity()));
        return new GymProfileResponse(Arrays.stream(BodyPart.values())
                .map(part -> GymRecordResponse.of(part, byPart.get(part)))
                .toList());
    }

    public record GymRecordResponse(
            @Schema(example = "CHEST") BodyPart bodyPart,
            @Schema(example = "true") boolean recorded,
            @Schema(description = "가슴·하체·어깨: 1RM(kg) / 등: 턱걸이 횟수", example = "80") BigDecimal recordValue,
            @Schema(description = "팔만 사용", example = "null") ArmLevel armLevel
    ) {

        static GymRecordResponse of(BodyPart part, UserGymRecord record) {
            if (record == null) {
                return new GymRecordResponse(part, false, null, null);
            }
            return new GymRecordResponse(part, true, record.getRecordValue(), record.getArmLevel());
        }
    }
}
