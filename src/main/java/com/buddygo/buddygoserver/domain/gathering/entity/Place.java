package com.buddygo.buddygoserver.domain.gathering.entity;

import com.buddygo.buddygoserver.global.entity.BaseCreatedEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.Point;

@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Entity
@Table(name = "places")
public class Place extends BaseCreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 지도에서 찍은 집결지는 NULL */
    @Column(name = "kakao_place_id", unique = true)
    private String kakaoPlaceId;

    @Column(nullable = false)
    private String name;

    @Column(name = "road_address")
    private String roadAddress;

    /** 지도 핀 묶음 기준 */
    @Column(name = "jibun_address", nullable = false)
    private String jibunAddress;

    /** 카카오 법정동 코드 */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "region_code", nullable = false, length = 10)
    private String regionCode;

    /** geometry(Point,4326) · GIST 인덱스는 마이그레이션에서 추가 */
    @Column(nullable = false, columnDefinition = "geometry(Point,4326)")
    private Point location;

    /** 장소 검색에 없는 곳이나 넓은 장소에서 지도로 찍은 집결지. 카카오 장소 ID 가 없어 매번 새로 만든다. */
    public static Place pinned(String name, String roadAddress, String jibunAddress, String regionCode, Point location) {
        return Place.builder()
                .name(name)
                .roadAddress(roadAddress)
                .jibunAddress(jibunAddress)
                .regionCode(regionCode)
                .location(location)
                .build();
    }
}
