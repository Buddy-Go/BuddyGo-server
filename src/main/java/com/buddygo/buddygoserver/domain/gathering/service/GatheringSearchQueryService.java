package com.buddygo.buddygoserver.domain.gathering.service;

import com.buddygo.buddygoserver.domain.application.repository.ParticipationRepository;
import com.buddygo.buddygoserver.domain.application.repository.ParticipationRepository.ConfirmedCount;
import com.buddygo.buddygoserver.domain.gathering.config.GatheringProperties;
import com.buddygo.buddygoserver.domain.gathering.dto.request.GatheringSearchRequest;
import com.buddygo.buddygoserver.domain.gathering.dto.response.GatheringCardResponse;
import com.buddygo.buddygoserver.domain.gathering.dto.response.GatheringPinsResponse;
import com.buddygo.buddygoserver.domain.gathering.dto.response.GatheringSearchResponse;
import com.buddygo.buddygoserver.domain.gathering.entity.Gathering;
import com.buddygo.buddygoserver.domain.gathering.enums.TimeSlot;
import com.buddygo.buddygoserver.domain.gathering.exception.GatheringErrorType;
import com.buddygo.buddygoserver.domain.gathering.repository.GatheringLikeRepository;
import com.buddygo.buddygoserver.domain.gathering.repository.GatheringRepository;
import com.buddygo.buddygoserver.domain.gathering.repository.GatheringSearchCondition;
import com.buddygo.buddygoserver.domain.gathering.repository.GatheringSearchCondition.Bbox;
import com.buddygo.buddygoserver.domain.gathering.repository.GatheringSearchCondition.Point;
import com.buddygo.buddygoserver.domain.gathering.repository.GatheringSearchCondition.ValueRange;
import com.buddygo.buddygoserver.domain.gathering.repository.GatheringSearchCursor;
import com.buddygo.buddygoserver.domain.gathering.repository.GatheringSearchRepository;
import com.buddygo.buddygoserver.domain.gathering.repository.GatheringSearchRepository.Hit;
import com.buddygo.buddygoserver.domain.sport.gym.enums.BodyPart;
import com.buddygo.buddygoserver.global.exception.CustomException;
import com.buddygo.buddygoserver.global.time.InstantRange;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** 탐색: 검색 영역 안의 모집중 모임을 카드 리스트와 지도 핀으로 보여준다(서비스 규칙 정의서 2·3장). */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GatheringSearchQueryService {

    private final GatheringSearchRepository gatheringSearchRepository;
    private final GatheringRepository gatheringRepository;
    private final ParticipationRepository participationRepository;
    private final GatheringLikeRepository gatheringLikeRepository;
    private final GatheringProperties gatheringProperties;
    private final Clock clock;

    /**
     * 무한 스크롤: 정렬 키 커서 뒤의 카드를 size 개까지 준다. 정렬·커서 처리는 ID 조회 한 번으로 끝내고,
     * 카드 내용은 그 ID 들로 모아서 읽는다(카드 수만큼 쿼리하지 않음).
     */
    public GatheringSearchResponse search(Long viewerId, GatheringSearchRequest request, String cursor, int size) {
        GatheringSearchCondition condition = toCondition(viewerId, request);
        GatheringSearchCursor after = cursor == null ? null : GatheringSearchCursor.decode(cursor);
        // 거리순 커서를 시작 시각순 요청에 쓰는 것처럼 정렬 방식이 다르면 이어 받을 수 없다
        if (after != null && (after.distanceMeters() == null) != (condition.origin() == null)) {
            throw new CustomException(GatheringErrorType.INVALID_SEARCH_CURSOR);
        }
        List<Hit> hits = gatheringSearchRepository.search(condition, after, size + 1);
        boolean hasNext = hits.size() > size;
        if (hasNext) {
            hits = hits.subList(0, size);
        }
        if (hits.isEmpty()) {
            return new GatheringSearchResponse(List.of(), null);
        }

        List<Long> ids = hits.stream().map(Hit::gatheringId).toList();
        Map<Long, Gathering> gatherings = gatheringRepository.findAllWithHostAndPlace(ids).stream()
                .collect(Collectors.toMap(Gathering::getId, Function.identity()));
        Map<Long, Long> confirmedCounts = participationRepository.countConfirmedByGatheringIds(ids).stream()
                .collect(Collectors.toMap(ConfirmedCount::getGatheringId, ConfirmedCount::getCount));
        Set<Long> liked = gatheringLikeRepository.findLikedGatheringIds(viewerId, ids);

        List<GatheringCardResponse> cards = hits.stream()
                // ID 조회와 카드 조회 사이에 지워진 모임은 뺀다
                .filter(hit -> gatherings.containsKey(hit.gatheringId()))
                .map(hit -> GatheringCardResponse.of(gatherings.get(hit.gatheringId()), hit.distanceMeters(),
                        confirmedCounts.getOrDefault(hit.gatheringId(), 0L), liked.contains(hit.gatheringId())))
                .toList();
        return new GatheringSearchResponse(cards, hasNext ? hits.getLast().toCursor().encode() : null);
    }

    public GatheringPinsResponse pins(Long viewerId, GatheringSearchRequest request) {
        return GatheringPinsResponse.from(gatheringSearchRepository.pins(toCondition(viewerId, request)));
    }

    private GatheringSearchCondition toCondition(Long viewerId, GatheringSearchRequest r) {
        Instant now = Instant.now(clock);
        Set<BodyPart> parts = r.parts() == null || r.parts().isEmpty() ? Set.of() : EnumSet.copyOf(r.parts());
        Map<BodyPart, ValueRange> valueRanges = valueRanges(r, parts);
        if (r.armLevel() != null && !parts.contains(BodyPart.ARM)) {
            throw invalidCondition();
        }
        return new GatheringSearchCondition(
                // 데드라인(시작 - deadline-before-start)이 지난 모임은 신청을 받지 않는다
                now.plus(gatheringProperties.deadlineBeforeStart()),
                bbox(r),
                r.regionCode() == null ? null : regionPrefix(r.regionCode()),
                r.parcelKey(),
                origin(r),
                timeRanges(r),
                parts,
                valueRanges,
                r.armLevel(),
                r.gender(),
                r.minManner(),
                Boolean.TRUE.equals(r.likedOnly()) ? viewerId : null);
    }

    /**
     * 지도 범위(네 값 모두)와 구·동 중 하나. 묶인 핀을 누른 경우에는 parcelKey 만으로도 된다: 지도를 옮긴 뒤에도
     * 그 핀의 모임이 나오도록 영역 없이 지번으로만 거른다(다른 조건은 그대로 건다).
     */
    private static Bbox bbox(GatheringSearchRequest r) {
        long given = Stream.of(r.swLat(), r.swLng(), r.neLat(), r.neLng()).filter(v -> v != null).count();
        boolean hasBbox = given == 4;
        boolean hasRegion = r.regionCode() != null;
        if ((given != 0 && !hasBbox) || (hasBbox && hasRegion) || (!hasBbox && !hasRegion && r.parcelKey() == null)) {
            throw new CustomException(GatheringErrorType.INVALID_SEARCH_AREA);
        }
        if (!hasBbox) {
            return null;
        }
        if (r.swLat() > r.neLat() || r.swLng() > r.neLng()) {
            throw new CustomException(GatheringErrorType.INVALID_SEARCH_AREA);
        }
        return new Bbox(r.swLat(), r.swLng(), r.neLat(), r.neLng());
    }

    /**
     * 카카오 b_code(법정동 10자리)에서 비교할 앞자리. 구·군·일반구 코드는 뒤 5자리가 0 이라 앞 5자리,
     * 동 코드는 뒤 2자리(리)가 0 이라 앞 8자리로 그 안의 장소를 모두 거른다.
     */
    private static String regionPrefix(String code) {
        if (code.endsWith("00000")) {
            return code.substring(0, 5);
        }
        return code.endsWith("00") ? code.substring(0, 8) : code;
    }

    private static Point origin(GatheringSearchRequest r) {
        if ((r.lat() == null) != (r.lng() == null)) {
            throw invalidCondition();
        }
        return r.lat() == null ? null : new Point(r.lat(), r.lng());
    }

    /** 날짜만 있으면 그날 하루, 시간대를 고르면 시간대마다, 직접 고른 범위면 그 범위. 모두 KST·시작 시각 기준이다. */
    private List<InstantRange> timeRanges(GatheringSearchRequest r) {
        Set<TimeSlot> slots = r.timeSlots() == null ? Set.of() : r.timeSlots();
        boolean custom = r.fromTime() != null || r.toTime() != null;
        LocalDate date = r.date();
        if (date == null) {
            if (!slots.isEmpty() || custom) {
                throw invalidCondition();
            }
            return List.of();
        }
        if (custom && (!slots.isEmpty() || r.fromTime() == null || r.toTime() == null)) {
            throw invalidCondition();
        }
        if (custom) {
            return List.of(InstantRange.of(date, r.fromTime(), r.toTime(), clock.getZone()));
        }
        if (slots.isEmpty()) {
            return List.of(InstantRange.of(date, LocalTime.MIDNIGHT, LocalTime.MIDNIGHT, clock.getZone()));
        }
        return slots.stream().sorted().map(slot -> slot.on(date, clock.getZone())).toList();
    }

    /** 부위별 기준 범위. 부위를 먼저 골라야 범위를 정할 수 있다(규칙 정의서 3장). */
    private static Map<BodyPart, ValueRange> valueRanges(GatheringSearchRequest r, Set<BodyPart> parts) {
        Map<BodyPart, ValueRange> ranges = new EnumMap<>(BodyPart.class);
        putRange(ranges, parts, BodyPart.CHEST, r.chestMin(), r.chestMax());
        putRange(ranges, parts, BodyPart.BACK, r.backMin(), r.backMax());
        putRange(ranges, parts, BodyPart.LEGS, r.legsMin(), r.legsMax());
        putRange(ranges, parts, BodyPart.SHOULDER, r.shoulderMin(), r.shoulderMax());
        return ranges;
    }

    private static void putRange(Map<BodyPart, ValueRange> ranges, Set<BodyPart> parts, BodyPart part,
                                 BigDecimal min, BigDecimal max) {
        if (min == null && max == null) {
            return;
        }
        if (!parts.contains(part) || (min != null && max != null && min.compareTo(max) > 0)) {
            throw invalidCondition();
        }
        ranges.put(part, new ValueRange(min, max));
    }

    private static CustomException invalidCondition() {
        return new CustomException(GatheringErrorType.INVALID_SEARCH_CONDITION);
    }
}
