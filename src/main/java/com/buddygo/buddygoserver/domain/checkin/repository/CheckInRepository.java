package com.buddygo.buddygoserver.domain.checkin.repository;

import com.buddygo.buddygoserver.domain.checkin.entity.CheckIn;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface CheckInRepository extends JpaRepository<CheckIn, Long> {

    /**
     * 모임의 확정 참가자(호스트 포함) 중 체크인 기록이 없는 사람을 노쇼로 남기고, 새로 노쇼가 된 참여 ID 를 돌려준다.
     * 경계 시각에 들어온 체크인과 겹쳐도 배치가 실패하지 않도록 check_ins_pkey 충돌은 건너뛴다.
     */
    @Query(nativeQuery = true, value = """
            INSERT INTO check_ins (participation_id, attendance)
            SELECT p.id, 'NO_SHOW' FROM participations p
            WHERE p.gathering_id = :gatheringId AND p.status = 'CONFIRMED'
            ON CONFLICT (participation_id) DO NOTHING
            RETURNING participation_id""")
    List<Long> insertNoShows(Long gatheringId);
}
