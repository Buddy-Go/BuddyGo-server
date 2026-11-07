package com.buddygo.buddygoserver.domain.user.repository;

import com.buddygo.buddygoserver.domain.application.entity.Participation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

import java.time.Instant;

/** 내 활동은 모임당 가장 최근 신청 한 건으로 표시한다. 과거 신청 행은 DB에 보존한다. */
public interface MyActivityRepository extends Repository<Participation, Long> {

    String UPCOMING = """
            (g.endAt > :now and g.status not in ('FINISHED', 'CANCELED')
             and p.status in ('PENDING', 'CONFIRMED'))
            """;

    String FILTER = """
            where p.user.id = :userId and g.deletedAt is null
              and ((:host = true and g.host.id = :userId) or (:host = false and g.host.id <> :userId))
              and p.id = (select max(latest.id) from Participation latest
                          where latest.user.id = :userId and latest.gathering.id = g.id)
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
