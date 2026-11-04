package com.buddygo.buddygoserver.domain.sport.gym.criteria;

import com.buddygo.buddygoserver.domain.sport.gym.config.GymCriteriaProperties;
import com.buddygo.buddygoserver.domain.sport.gym.entity.GatheringGymPart;
import com.buddygo.buddygoserver.domain.sport.gym.entity.UserGymRecord;
import com.buddygo.buddygoserver.domain.sport.gym.enums.BodyPart;
import com.buddygo.buddygoserver.domain.sport.gym.enums.CriteriaMatch;
import com.buddygo.buddygoserver.domain.sport.gym.enums.RecordComparison;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 헬스 기준 비교. 모임 상세의 기준 일치 판정(8장)과 탐색의 기준 필터(3장)가 같은 비교를 쓰도록 여기에 둔다.
 * 중량 부위는 ±weightToleranceKg, 등은 ±repsTolerance 범위, 팔은 숙련도가 같아야 맞다.
 */
@Component
@RequiredArgsConstructor
public class GymCriteria {

    private final GymCriteriaProperties properties;

    /** 기록값을 가운데로 한 기준 범위. 아래쪽은 0 에서 자른다. 팔(숙련도)은 범위가 없다. */
    public Range rangeOf(BodyPart part, BigDecimal center) {
        BigDecimal tolerance = switch (part.getRecordType()) {
            case WEIGHT -> properties.weightToleranceKg();
            case REPS -> properties.repsTolerance();
            case LEVEL -> throw new IllegalArgumentException("숙련도 부위는 범위가 없습니다: " + part);
        };
        return new Range(center.subtract(tolerance).max(BigDecimal.ZERO), center.add(tolerance));
    }

    /**
     * 부위 하나에서 내 기록을 모임 기준과 비교한다. 팔은 숙련도가 같아야 맞고(규칙 정의서 8장),
     * 다르면 높낮이만 알려준다.
     */
    public RecordComparison compare(GatheringGymPart criteria, UserGymRecord mine) {
        if (mine == null) {
            return RecordComparison.NO_MY_RECORD;
        }
        if (!criteria.hasRecord()) {
            return RecordComparison.HOST_HIDDEN;
        }
        BodyPart part = criteria.getBodyPart();
        if (part.getRecordType() == BodyPart.RecordType.LEVEL) {
            int diff = mine.getArmLevel().compareTo(criteria.getArmLevel());
            return diff == 0 ? RecordComparison.WITHIN : diff > 0 ? RecordComparison.ABOVE : RecordComparison.BELOW;
        }
        Range range = rangeOf(part, criteria.getRecordValue());
        if (mine.getRecordValue().compareTo(range.min()) < 0) {
            return RecordComparison.BELOW;
        }
        return mine.getRecordValue().compareTo(range.max()) > 0 ? RecordComparison.ABOVE : RecordComparison.WITHIN;
    }

    /**
     * 모임의 부위 기준 전체와 내 기록을 비교한다. 비교할 수 있는 부위가 없으면 result 는 null 이다.
     *
     * @param mine 내 부위별 기록. 기록이 없는 부위는 빠져 있다.
     */
    public Judgement judge(Collection<GatheringGymPart> criteria, Map<BodyPart, UserGymRecord> mine) {
        List<BodyPart> matched = new ArrayList<>();
        List<BodyPart> mismatched = new ArrayList<>();
        List<BodyPart> withoutMyRecord = new ArrayList<>();
        criteria.stream().sorted(Comparator.comparing(GatheringGymPart::getBodyPart)).forEach(c -> {
            RecordComparison comparison = compare(c, mine.get(c.getBodyPart()));
            if (comparison == RecordComparison.NO_MY_RECORD) {
                withoutMyRecord.add(c.getBodyPart());
            } else if (Boolean.TRUE.equals(comparison.matches())) {
                matched.add(c.getBodyPart());
            } else if (Boolean.FALSE.equals(comparison.matches())) {
                mismatched.add(c.getBodyPart());
            }
        });
        CriteriaMatch result;
        if (matched.isEmpty() && mismatched.isEmpty()) {
            result = null;
        } else if (mismatched.isEmpty()) {
            result = CriteriaMatch.MATCH;
        } else if (matched.isEmpty()) {
            result = CriteriaMatch.MISMATCH;
        } else {
            result = CriteriaMatch.PARTIAL;
        }
        return new Judgement(result, List.copyOf(mismatched), List.copyOf(withoutMyRecord));
    }

    /** [min, max] 닫힌 범위 */
    public record Range(BigDecimal min, BigDecimal max) {

        public boolean contains(BigDecimal value) {
            return value != null && value.compareTo(min) >= 0 && value.compareTo(max) <= 0;
        }
    }

    /**
     * @param result          비교할 수 있는 부위가 없으면 null
     * @param mismatchedParts 범위 밖 부위('일부만 맞아요'에 표시)
     * @param partsWithoutMyRecord 내 기록이 없는 부위('기록 추가' 표시)
     */
    public record Judgement(CriteriaMatch result, List<BodyPart> mismatchedParts, List<BodyPart> partsWithoutMyRecord) {
    }
}
