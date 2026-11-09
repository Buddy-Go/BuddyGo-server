package com.buddygo.buddygoserver.domain.manner.repository;

import com.buddygo.buddygoserver.domain.manner.entity.MannerTemperatureLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public interface MannerTemperatureLogRepository extends JpaRepository<MannerTemperatureLog, Long> {

    /**
     * 출석(노쇼·지각) 이력을 넣고, 넣었으면 새 id 를, 같은 (사람, 모임, 사유)가 이미 있으면 빈 목록을 돌려준다.
     * uk_manner_temperature_logs_attendance(V7) 충돌은 건너뛰어, 중복 호출이 이중 감점도 트랜잭션 실패도 만들지 않게 한다.
     * 네이티브 INSERT 라 JPA Auditing 이 돌지 않으므로 created_at 을 넘긴다.
     */
    @Query(nativeQuery = true, value = """
            INSERT INTO manner_temperature_logs (user_id, delta, reason, gathering_id, created_at)
            VALUES (:userId, :delta, :reason, :gatheringId, :createdAt)
            ON CONFLICT (user_id, gathering_id, reason) WHERE reason IN ('NO_SHOW', 'LATE') DO NOTHING
            RETURNING id""")
    List<Long> insertAttendanceIgnoringDuplicate(Long userId, BigDecimal delta, String reason, Long gatheringId,
                                                 Instant createdAt);
}
