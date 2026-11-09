package com.buddygo.buddygoserver.domain.gathering.repository;

import com.buddygo.buddygoserver.domain.gathering.entity.Place;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;

public interface PlaceRepository extends JpaRepository<Place, Long> {

    /**
     * 카카오 장소를 찾거나 만들고 id 를 돌려준다. 같은 장소로 동시에 개설해도 실패하지 않도록
     * uk_places_kakao_place_id 충돌은 기존 행을 그대로 쓴다(값은 바꾸지 않는 no-op UPDATE 로 RETURNING 을 받는다).
     * 네이티브 INSERT 라 JPA Auditing 이 돌지 않으므로 created_at 을 넘긴다.
     */
    @Query(nativeQuery = true, value = """
            INSERT INTO places (kakao_place_id, name, road_address, jibun_address, region_code, location, created_at)
            VALUES (:kakaoPlaceId, :name, :roadAddress, :jibunAddress, :regionCode,
                    ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326), :createdAt)
            ON CONFLICT (kakao_place_id) DO UPDATE SET kakao_place_id = EXCLUDED.kakao_place_id
            RETURNING id""")
    Long upsertKakaoPlace(String kakaoPlaceId, String name, String roadAddress, String jibunAddress,
                          String regionCode, double latitude, double longitude, Instant createdAt);

    /**
     * 장소에서 좌표까지의 거리(m, 반올림). 탐색 거리순과 같은 식이다: geography 로 바꿔 구면 거리를 재고,
     * ST_MakePoint 는 (경도, 위도) 순서다.
     */
    @Query(nativeQuery = true, value = """
            SELECT CAST(ROUND(ST_Distance(p.location::geography,
                        ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)::geography)) AS INTEGER)
            FROM places p WHERE p.id = :placeId""")
    int distanceMeters(Long placeId, double latitude, double longitude);
}
