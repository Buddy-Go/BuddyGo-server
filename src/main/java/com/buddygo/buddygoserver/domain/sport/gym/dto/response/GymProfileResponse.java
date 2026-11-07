package com.buddygo.buddygoserver.domain.sport.gym.dto.response;

import com.buddygo.buddygoserver.domain.sport.gym.criteria.GymCriteria;
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
public record GymProfileResponse(
        List<GymRecordResponse> records,
        @Schema(description = """
                탐색에서 기준 설정을 켰을 때의 범위 초깃값(내 기록 ± 서버 설정 허용치). 기록이 있는 중량·횟수 부위만 담는다.
                팔은 records 의 armLevel 을 그대로 쓴다.""")
        List<SearchRangeResponse> searchRanges
) {

    public static GymProfileResponse from(Collection<UserGymRecord> saved, GymCriteria gymCriteria) {
        Map<BodyPart, UserGymRecord> byPart = saved.stream()
                .collect(Collectors.toMap(UserGymRecord::getBodyPart, Function.identity()));
        return new GymProfileResponse(
                Arrays.stream(BodyPart.values())
                        .map(part -> GymRecordResponse.of(part, byPart.get(part)))
                        .toList(),
                Arrays.stream(BodyPart.values())
                        .filter(part -> part.getRecordType() != BodyPart.RecordType.LEVEL && byPart.containsKey(part))
                        .map(part -> SearchRangeResponse.of(part, gymCriteria.rangeOf(part, byPart.get(part).getRecordValue())))
                        .toList());
    }

    public record SearchRangeResponse(
            @Schema(example = "CHEST") BodyPart bodyPart,
            @Schema(description = "하한(0 미만은 0)", example = "70") BigDecimal minValue,
            @Schema(description = "상한", example = "90") BigDecimal maxValue
    ) {

        static SearchRangeResponse of(BodyPart part, GymCriteria.Range range) {
            return new SearchRangeResponse(part, range.min(), range.max());
        }
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
