package com.buddygo.buddygoserver.domain.application.repository;

import com.buddygo.buddygoserver.domain.application.entity.Participation;
import com.buddygo.buddygoserver.domain.application.enums.ParticipationStatus;
import com.buddygo.buddygoserver.domain.application.enums.RejectReason;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ParticipationRepository extends JpaRepository<Participation, Long> {

    /** 화면의 '확정/정원' 숫자. 호스트의 확정 레코드도 센다. */
    long countByGatheringIdAndStatus(Long gatheringId, ParticipationStatus status);

    /**
     * 규칙의 '확정 참가자' 수. 호스트도 확정 레코드를 가지므로 호스트를 빼고 센다.
     * 수정 범위(전체 vs 제목·설명·상세주소)와 삭제/취소 갈림을 이 값으로 정한다.
     */
    @Query("""
            select count(p) from Participation p
            where p.gathering.id = :gatheringId
              and p.status = com.buddygo.buddygoserver.domain.application.enums.ParticipationStatus.CONFIRMED
              and p.user.id <> p.gathering.host.id""")
    long countConfirmedExcludingHost(Long gatheringId);

    /** 진행 중인 신청·참여(PENDING·CONFIRMED). 부분 유니크 인덱스로 한 사람에 하나뿐이다. */
    @Query("""
            select p from Participation p
            where p.gathering.id = :gatheringId and p.user.id = :userId
              and p.status in (com.buddygo.buddygoserver.domain.application.enums.ParticipationStatus.PENDING,
                               com.buddygo.buddygoserver.domain.application.enums.ParticipationStatus.CONFIRMED)""")
    Optional<Participation> findActive(Long gatheringId, Long userId);

    @Query("""
            select p from Participation p
            join fetch p.user
            where p.gathering.id = :gatheringId
              and p.status = com.buddygo.buddygoserver.domain.application.enums.ParticipationStatus.CONFIRMED
            order by p.decidedAt, p.id""")
    List<Participation> findConfirmedWithUser(Long gatheringId);

    @Query("""
            select p from Participation p
            join fetch p.user
            where p.gathering.id = :gatheringId
              and p.status = com.buddygo.buddygoserver.domain.application.enums.ParticipationStatus.PENDING
            order by p.appliedAt, p.id""")
    List<Participation> findPendingWithUser(Long gatheringId);

    interface ConfirmedCount {
        Long getGatheringId();

        long getCount();
    }

    /** 모임마다 확정 인원(호스트 포함) */
    @Query("""
            select p.gathering.id as gatheringId, count(p) as count from Participation p
            where p.gathering.id in :gatheringIds
              and p.status = com.buddygo.buddygoserver.domain.application.enums.ParticipationStatus.CONFIRMED
            group by p.gathering.id""")
    List<ConfirmedCount> countConfirmedByGatheringIds(Collection<Long> gatheringIds);

    /**
     * 사람들의 현재 활동(버디에게 보이는 것): 개설했거나(호스트 확정 레코드) 신청(대기)·확정한 모임 중
     * 아직 끝나지 않은 모집중·마감·진행중 모임. 상태 전이가 늦을 때를 대비해 시각으로도 거른다:
     * 종료 시각이 지난 모임과 데드라인이 지난 모집중 모임(정원 미달 취소 대상)은 빼고, 노쇼한 참여도 뺀다.
     * 시작이 이른 순.
     */
    @Query("""
            select p from Participation p
            join fetch p.gathering g
            join fetch g.place
            where p.user.id in :userIds
              and p.status in (com.buddygo.buddygoserver.domain.application.enums.ParticipationStatus.PENDING,
                               com.buddygo.buddygoserver.domain.application.enums.ParticipationStatus.CONFIRMED)
             
              and g.status in (com.buddygo.buddygoserver.domain.gathering.enums.GatheringStatus.RECRUITING,
                               com.buddygo.buddygoserver.domain.gathering.enums.GatheringStatus.CLOSED,
                               com.buddygo.buddygoserver.domain.gathering.enums.GatheringStatus.IN_PROGRESS)
              and g.endAt > :now
              and (g.status <> com.buddygo.buddygoserver.domain.gathering.enums.GatheringStatus.RECRUITING
                   or g.startAt > :recruitingStartAfter)
              and not exists (
                  select 1 from CheckIn c
                  where c.id = p.id
                    and c.attendance = com.buddygo.buddygoserver.domain.checkin.enums.Attendance.NO_SHOW)
            order by g.startAt, g.id""")
    List<Participation> findActivities(Collection<Long> userIds, Instant now, Instant recruitingStartAfter);

    /** 모임 삭제·취소 때 남은 대기 신청을 모두 자동 거절한다. */
    @Modifying(flushAutomatically = true)
    @Query("""
            update Participation p
            set p.status = com.buddygo.buddygoserver.domain.application.enums.ParticipationStatus.REJECTED,
                p.rejectReason = :reason,
                p.decidedAt = :now
            where p.gathering.id = :gatheringId
              and p.status = com.buddygo.buddygoserver.domain.application.enums.ParticipationStatus.PENDING""")
    int rejectAllPending(Long gatheringId, RejectReason reason, Instant now);
}
