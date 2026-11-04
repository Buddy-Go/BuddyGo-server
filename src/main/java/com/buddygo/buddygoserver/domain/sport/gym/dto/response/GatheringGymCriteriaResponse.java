package com.buddygo.buddygoserver.domain.sport.gym.dto.response;

import com.buddygo.buddygoserver.domain.sport.gym.criteria.GymCriteria;
import com.buddygo.buddygoserver.domain.sport.gym.entity.UserGymRecord;
import com.buddygo.buddygoserver.domain.sport.gym.enums.ArmLevel;
import com.buddygo.buddygoserver.domain.sport.gym.enums.BodyPart;
import com.buddygo.buddygoserver.domain.sport.gym.enums.CriteriaMatch;
import com.buddygo.buddygoserver.domain.sport.gym.enums.RecordComparison;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

/** 모임의 헬스 기준. 호스트 기록은 값이 아니라 범위로 보여준다 (예: 벤치프레스 80kg → 가슴 70–90kg). */
public record GatheringGymCriteriaResponse(
        List<PartResponse> parts,
        @Schema(description = "기준 일치 판정. 호스트 본인에게는 null") MatchResponse match
) {

    public record PartResponse(
            @Schema(example = "CHEST") BodyPart bodyPart,
            @Schema(description = "호스트 기록 표시 여부. 숨겼거나 기록이 없으면 false 이고 값은 null", example = "true")
            boolean recordShown,
            @Schema(description = "가슴·하체·어깨(kg) · 등(회) 범위 아래 끝", example = "70") BigDecimal minValue,
            @Schema(description = "범위 위 끝", example = "90") BigDecimal maxValue,
            @Schema(description = "팔만 사용", example = "null") ArmLevel armLevel,
            @Schema(description = "내 기록(kg 또는 회). 기록이 없거나 호스트 본인이면 null", example = "95")
            BigDecimal myRecordValue,
            @Schema(description = "내 팔 숙련도. 팔만 사용", example = "null") ArmLevel myArmLevel,
            @Schema(description = "WITHIN 범위 안 / ABOVE 범위보다 높음 / BELOW 범위보다 낮음 / NO_MY_RECORD 내 기록 없음 "
                    + "/ HOST_HIDDEN 호스트 기록 숨김. 호스트 본인에게는 null", example = "ABOVE")
            RecordComparison comparison
    ) {

        public static PartResponse of(BodyPart part, GymCriteria.Range range, ArmLevel armLevel,
                                      UserGymRecord mine, RecordComparison comparison) {
            boolean shown = range != null || armLevel != null;
            return new PartResponse(part, shown,
                    range == null ? null : range.min(), range == null ? null : range.max(), armLevel,
                    mine == null ? null : mine.getRecordValue(), mine == null ? null : mine.getArmLevel(),
                    comparison);
        }
    }

    public record MatchResponse(
            @Schema(description = "MATCH 맞아요 / PARTIAL 일부만 맞아요 / MISMATCH 맞지 않아요. 비교할 부위가 없으면 null",
                    example = "PARTIAL")
            CriteriaMatch result,
            @Schema(description = "범위 밖 부위", example = "[\"LEGS\"]") List<BodyPart> mismatchedParts,
            @Schema(description = "내 기록이 없는 부위('기록 추가' 표시)", example = "[\"ARM\"]")
            List<BodyPart> partsWithoutMyRecord
    ) {

        public static MatchResponse from(GymCriteria.Judgement judgement) {
            return new MatchResponse(judgement.result(), judgement.mismatchedParts(), judgement.partsWithoutMyRecord());
        }
    }
}
