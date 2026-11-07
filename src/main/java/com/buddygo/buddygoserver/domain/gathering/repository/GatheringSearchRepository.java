package com.buddygo.buddygoserver.domain.gathering.repository;

import com.buddygo.buddygoserver.domain.gathering.enums.GatheringStatus;
import com.buddygo.buddygoserver.domain.gathering.repository.GatheringSearchCondition.Bbox;
import com.buddygo.buddygoserver.domain.gathering.repository.GatheringSearchCondition.ValueRange;
import com.buddygo.buddygoserver.domain.sport.gym.enums.BodyPart;
import com.buddygo.buddygoserver.global.time.InstantRange;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/**
 * 탐색(리스트·지도 핀) 조회. 조건 조합이 많아 있는 조건만 WHERE 에 붙이는 네이티브 SQL 로 만든다.
 * 리스트는 정렬·커서 처리한 모임 ID 만 돌려주고, 카드에 필요한 엔티티·집계는 서비스가 그 ID 로 한 번에 읽는다.
 */
@Repository
@RequiredArgsConstructor
public class GatheringSearchRepository {

    private static final String FROM =
            " FROM gatherings g JOIN places p ON p.id = g.place_id JOIN users h ON h.id = g.host_id";

    /** 내 위치에서 모임 장소까지의 거리(m). geography 로 바꿔 구면 거리를 잰다. ST_MakePoint 는 (경도, 위도) 순서다. */
    private static final String DISTANCE =
            "ST_Distance(p.location::geography, ST_SetSRID(ST_MakePoint(:originLng, :originLat), 4326)::geography)";

    private final EntityManager entityManager;

    /**
     * 거리순(내 위치가 없으면 시작 시각순)으로 정렬해 커서 뒤의 모임 ID 를 limit 개까지 읽는다.
     * 정렬 키 (거리, 시작 시각, id) 를 행 비교로 이어 받으므로 OFFSET 과 달리 중간에 목록이 바뀌어도 밀리지 않는다.
     *
     * @param after 첫 페이지면 null. 정렬 방식(거리 유무)이 조건과 같아야 한다
     */
    public List<Hit> search(GatheringSearchCondition condition, GatheringSearchCursor after, int limit) {
        Where where = where(condition);
        boolean hasOrigin = condition.origin() != null;
        if (after != null) {
            if (hasOrigin) {
                where.and("(" + DISTANCE + ", g.start_at, g.id) > (:afterDistance, :afterStartAt, :afterId)");
                where.param("afterDistance", after.distanceMeters());
            } else {
                where.and("(g.start_at, g.id) > (:afterStartAt, :afterId)");
            }
            where.param("afterStartAt", after.startAt()).param("afterId", after.gatheringId());
        }
        String sql = "SELECT g.id, g.start_at, " + (hasOrigin ? DISTANCE : "NULL") + " AS distance"
                + FROM + where.sql()
                + " ORDER BY " + (hasOrigin ? "distance, " : "") + "g.start_at, g.id"
                + " LIMIT :limit";
        Query query = entityManager.createNativeQuery(sql);
        where.bindTo(query);
        if (hasOrigin) {
            query.setParameter("originLat", condition.origin().lat());
            query.setParameter("originLng", condition.origin().lng());
        }
        query.setParameter("limit", limit);

        List<Hit> hits = new ArrayList<>();
        for (Object row : query.getResultList()) {
            Object[] columns = (Object[]) row;
            hits.add(new Hit(((Number) columns[0]).longValue(), toInstant(columns[1]),
                    columns[2] == null ? null : ((Number) columns[2]).doubleValue()));
        }
        return hits;
    }

    private static Instant toInstant(Object value) {
        return switch (value) {
            case Instant instant -> instant;
            case OffsetDateTime offsetDateTime -> offsetDateTime.toInstant();
            case Timestamp timestamp -> timestamp.toInstant();
            default -> throw new IllegalStateException("start_at 타입을 알 수 없습니다: " + value.getClass());
        };
    }

    /**
     * 지번 주소가 같은 모임을 핀 하나로 묶는다. 지도에서 찍은 집결지는 매번 새 장소로 저장되어 같은 지번이라도
     * 좌표가 조금씩 다를 수 있으므로, 핀 좌표는 묶인 장소들의 중심점으로 한다.
     */
    public List<Pin> pins(GatheringSearchCondition condition) {
        Where where = where(condition);
        String sql = """
                SELECT p.jibun_address, count(*),
                       ST_Y(ST_Centroid(ST_Collect(p.location))), ST_X(ST_Centroid(ST_Collect(p.location)))"""
                + FROM + where.sql()
                + " GROUP BY p.jibun_address ORDER BY p.jibun_address";
        Query query = entityManager.createNativeQuery(sql);
        where.bindTo(query);

        List<Pin> pins = new ArrayList<>();
        for (Object row : query.getResultList()) {
            Object[] columns = (Object[]) row;
            pins.add(new Pin((String) columns[0], ((Number) columns[1]).longValue(),
                    ((Number) columns[2]).doubleValue(), ((Number) columns[3]).doubleValue()));
        }
        return pins;
    }

    private static Where where(GatheringSearchCondition c) {
        Where w = new Where();
        // 지도에는 모집중만 나온다(규칙 정의서 2장). 상태 전이가 늦어도 데드라인이 지난 모임은 빼도록 시각으로도 거른다.
        w.and("g.status = :status", "status", GatheringStatus.RECRUITING.name());
        w.and("g.start_at > :startAfter", "startAfter", c.startAfter());

        if (c.bbox() != null) {
            Bbox b = c.bbox();
            w.and("p.location && ST_MakeEnvelope(:swLng, :swLat, :neLng, :neLat, 4326)");
            w.param("swLng", b.swLng()).param("swLat", b.swLat()).param("neLng", b.neLng()).param("neLat", b.neLat());
        }
        if (c.regionPrefix() != null) {
            // LIKE 'prefix%' 는 en_US 정렬 규칙에서 btree 인덱스를 못 탄다. 같은 뜻의 범위 조건은 idx_places_region_code 를 쓴다.
            w.and("p.region_code BETWEEN CAST(:regionFrom AS char(10)) AND CAST(:regionTo AS char(10))");
            w.param("regionFrom", pad(c.regionPrefix(), '0')).param("regionTo", pad(c.regionPrefix(), '9'));
        }
        if (c.parcelKey() != null) {
            w.and("p.jibun_address = :parcelKey", "parcelKey", c.parcelKey());
        }
        if (!c.timeRanges().isEmpty()) {
            StringJoiner any = new StringJoiner(" OR ", "(", ")");
            for (int i = 0; i < c.timeRanges().size(); i++) {
                InstantRange range = c.timeRanges().get(i);
                any.add("(g.start_at >= :timeFrom" + i + " AND g.start_at < :timeTo" + i + ")");
                w.param("timeFrom" + i, range.start()).param("timeTo" + i, range.end());
            }
            w.and(any.toString());
        }
        if (!c.parts().isEmpty()) {
            w.and("""
                    (SELECT count(*) FROM gathering_gym_parts gp
                     WHERE gp.gathering_id = g.id AND gp.body_part IN (:parts)) = :partCount""");
            w.param("parts", c.parts().stream().map(BodyPart::name).toList()).param("partCount", (long) c.parts().size());
        }
        if (c.hasCriteriaRange()) {
            // 호스트가 기록을 숨긴 부위는 값이 NULL 이라 어떤 비교도 참이 되지 않는다(범위 안으로 치지 않음).
            StringJoiner any = new StringJoiner(" OR ", "(", ")");
            for (Map.Entry<BodyPart, ValueRange> e : c.valueRanges().entrySet()) {
                String part = e.getKey().name();
                StringBuilder clause = new StringBuilder("(gp.body_part = '" + part + "'");
                if (e.getValue().min() != null) {
                    clause.append(" AND gp.record_value >= :min").append(part);
                    w.param("min" + part, e.getValue().min());
                }
                if (e.getValue().max() != null) {
                    clause.append(" AND gp.record_value <= :max").append(part);
                    w.param("max" + part, e.getValue().max());
                }
                any.add(clause.append(')'));
            }
            if (c.armLevel() != null) {
                any.add("(gp.body_part = '" + BodyPart.ARM.name() + "' AND gp.arm_level = :armLevel)");
                w.param("armLevel", c.armLevel().name());
            }
            w.and("EXISTS (SELECT 1 FROM gathering_gym_parts gp WHERE gp.gathering_id = g.id AND " + any + ")");
        }
        if (c.gender() != null) {
            w.and("g.gender_rule = :gender", "gender", c.gender().name());
        }
        if (c.minManner() != null) {
            w.and("h.manner_temperature >= :minManner", "minManner", c.minManner());
        }
        if (c.likedBy() != null) {
            w.and("EXISTS (SELECT 1 FROM gathering_likes l WHERE l.gathering_id = g.id AND l.user_id = :likedBy)",
                    "likedBy", c.likedBy());
        }
        return w;
    }

    private static String pad(String prefix, char fill) {
        return prefix + String.valueOf(fill).repeat(10 - prefix.length());
    }

    /** @param distanceMeters 내 위치가 없으면 null */
    public record Hit(Long gatheringId, Instant startAt, Double distanceMeters) {

        public GatheringSearchCursor toCursor() {
            return new GatheringSearchCursor(distanceMeters, startAt, gatheringId);
        }
    }

    public record Pin(String parcelKey, long gatheringCount, double latitude, double longitude) {
    }

    private static final class Where {
        private final StringJoiner clauses = new StringJoiner(" AND ", " WHERE ", "");
        private final Map<String, Object> params = new HashMap<>();

        void and(String clause) {
            clauses.add(clause);
        }

        void and(String clause, String name, Object value) {
            and(clause);
            param(name, value);
        }

        Where param(String name, Object value) {
            params.put(name, value);
            return this;
        }

        String sql() {
            return clauses.toString();
        }

        void bindTo(Query query) {
            params.forEach(query::setParameter);
        }
    }
}
