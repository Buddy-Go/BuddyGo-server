package com.buddygo.buddygoserver.domain.user.repository;

import com.buddygo.buddygoserver.domain.application.entity.Participation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

import java.time.Instant;

/**
 * 내 활동은 대기·확정 신청만 보여준다. 거절·신청 취소·나가기와 삭제된 모임은 빠진다.
 * 대기·확정 신청은 부분 유니크 인덱스로 모임당 하나뿐이라 한 모임이 두 번 나오지 않는다.
 */
public interface MyActivityRepository extends Repository<Participation, Long> {

    String UPCOMING = "(g.endAt > :now and g.status not in ('FINISHED', 'CANCELED'))";

    String FILTER = """
            where p.user.id = :userId
              and p.status in ('PENDING', 'CONFIRMED')
              and ((:host = true and g.host.id = :userId) or (:host = false and g.host.id <> :userId))
              and ((:upcoming = true and
            """ + UPCOMING + ") or (:upcoming = false and not " + UPCOMING + "))";

    @Query(value = """
            select p from Participation p
            join fetch p.gathering g
            join fetch g.host
            join fetch g.place
            """ + FILTER,
            countQuery = "select count(p) from Participation p join p.gathering g " + FILTER)
    Page<Participation> findMyActivities(Long userId, boolean host, boolean upcoming, Instant now, Pageable pageable);
}
