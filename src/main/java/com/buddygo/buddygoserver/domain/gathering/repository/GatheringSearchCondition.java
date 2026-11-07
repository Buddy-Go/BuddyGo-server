package com.buddygo.buddygoserver.domain.gathering.repository;

import com.buddygo.buddygoserver.domain.gathering.enums.GenderRule;
import com.buddygo.buddygoserver.domain.sport.gym.enums.ArmLevel;
import com.buddygo.buddygoserver.domain.sport.gym.enums.BodyPart;
import com.buddygo.buddygoserver.global.time.InstantRange;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 검증을 마친 탐색 조건. null·빈 값인 조건은 걸지 않는다.
 *
 * @param startAfter   모집중이어도 시작 시각이 이 시각 이후인 모임만(데드라인이 지난 모임은 정원 미달 취소 대상이라 뺀다)
 * @param bbox         지도 범위. regionPrefix 와 둘 중 하나만 있다
 * @param regionPrefix 법정동 코드 앞자리(5 또는 8자리)
 * @param origin       거리 기준점(내 위치). null 이면 거리 없이 시작 시각순
 * @param timeRanges   시작 시각이 이 중 하나에 들어가는 모임만(OR)
 * @param parts        이 부위를 모두 포함하는 모임만(AND)
 * @param valueRanges  기준 범위(팔을 뺀 중량·횟수 부위). armLevel 과 함께, 하나라도 범위 안이면 포함(OR)
 * @param likedBy      이 사용자가 찜한 모임만
 */
public record GatheringSearchCondition(
        Instant startAfter,
        Bbox bbox,
        String regionPrefix,
        String parcelKey,
        Point origin,
        List<InstantRange> timeRanges,
        Set<BodyPart> parts,
        Map<BodyPart, ValueRange> valueRanges,
        ArmLevel armLevel,
        GenderRule gender,
        BigDecimal minManner,
        Long likedBy
) {

    public boolean hasCriteriaRange() {
        return !valueRanges.isEmpty() || armLevel != null;
    }

    public record Bbox(double swLat, double swLng, double neLat, double neLng) {
    }

    public record Point(double lat, double lng) {
    }

    /** 닫힌 범위. 한쪽이 null 이면 그쪽은 열려 있다. */
    public record ValueRange(BigDecimal min, BigDecimal max) {
    }
}
