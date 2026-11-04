package com.buddygo.buddygoserver.domain.region.entity;

import com.buddygo.buddygoserver.domain.region.enums.RegionLevel;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 위치 조건(구·동) 검색용 법정동. 데이터는 V2 마이그레이션이 넣고 읽기 전용으로 쓴다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "regions")
public class Region {

    /** 법정동 코드 10자리 */
    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 10)
    private String code;

    /** 시도부터 이어 쓴 전체 이름. 예) 서울특별시 강남구 역삼동 */
    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RegionLevel level;

    /** places.region_code 를 LIKE 'prefix%' 로 거르는 값 */
    @Column(name = "code_prefix", nullable = false, length = 8)
    private String codePrefix;
}
