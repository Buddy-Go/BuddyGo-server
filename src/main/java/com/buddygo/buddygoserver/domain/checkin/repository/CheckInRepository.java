package com.buddygo.buddygoserver.domain.checkin.repository;

import com.buddygo.buddygoserver.domain.checkin.entity.CheckIn;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

/**
 * '참여'(함께 운동함)는 체크인한 것(정상·지각)을 말하고 노쇼는 빠진다. 체크인 뒤에 취소된 모임도 있을 수 있어
 * 실제로 진행된 모임(진행중·종료)만 센다. 함께 운동한 사람·참여 횟수·평가 대상이 모두 이 기준을 쓴다.
 */
public interface CheckInRepository extends JpaRepository<CheckIn, Long> {

    /**
     * 모임의 확정 참가자(호스트 포함) 중 체크인 기록이 없는 사람을 노쇼로 남기고, 이번에 새로 노쇼가 된 사람의 user id 를 돌려준다.
     * 경계 시각에 들어온 체크인과 겹쳐도 배치가 실패하지 않도록 check_ins_pkey 충돌은 건너뛴다. 이미 기록된 사람은 결과에
     * 없으므로, 여러 번 불러도 노쇼 페널티는 한 번만 들어간다.
     */
    @Query(nativeQuery = true, value = """
            WITH inserted AS (
                INSERT INTO check_ins (participation_id, attendance)
                SELECT p.id, 'NO_SHOW' FROM participations p
                WHERE p.gathering_id = :gatheringId AND p.status = 'CONFIRMED'
                ON CONFLICT (participation_id) DO NOTHING
                RETURNING participation_id)
            SELECT p.user_id FROM inserted i JOIN participations p ON p.id = i.participation_id""")
    List<Long> insertNoShows(Long gatheringId);

    /** 확정 참가자(호스트 포함) 중 아직 체크인 기록이 없는 사람의 user id. '운동 시작' 알림 대상이다. */
    @Query(nativeQuery = true, value = """
            SELECT p.user_id FROM participations p
            WHERE p.gathering_id = :gatheringId AND p.status = 'CONFIRMED'
              AND NOT EXISTS (SELECT 1 FROM check_ins c WHERE c.participation_id = p.id)""")
    List<Long> findConfirmedUserIdsWithoutCheckIn(Long gatheringId);

    /** 모임의 호스트가 체크인(정상·지각)했는지. 호스트 출석 보너스 판단에 쓴다. */
    @Query(nativeQuery = true, value = """
            SELECT EXISTS (
                SELECT 1
                FROM gatherings g
                JOIN participations p ON p.gathering_id = g.id AND p.user_id = g.host_id
                JOIN check_ins c ON c.participation_id = p.id AND c.attendance IN ('ON_TIME', 'LATE')
                WHERE g.id = :gatheringId)""")
    boolean hostAttended(Long gatheringId);

    /** 두 사람이 같은 모임에서 함께 체크인(정상·지각)한 적이 있는지 */
    @Query(nativeQuery = true, value = """
            SELECT EXISTS (
                SELECT 1
                FROM participations pa
                JOIN check_ins ca ON ca.participation_id = pa.id AND ca.attendance IN ('ON_TIME', 'LATE')
                JOIN gatherings g ON g.id = pa.gathering_id
                     AND g.status IN ('IN_PROGRESS', 'FINISHED')
                JOIN participations pb ON pb.gathering_id = g.id AND pb.user_id = :otherId
                JOIN check_ins cb ON cb.participation_id = pb.id AND cb.attendance IN ('ON_TIME', 'LATE')
                WHERE pa.user_id = :userId)""")
    boolean workedOutTogether(Long userId, Long otherId);

    interface Attendee {
        Long getUserId();

        String getNickname();

        boolean getHost();
    }

    /**
     * 모임에서 체크인(정상·지각)한 사람(호스트 포함). 평가는 이 사람들끼리 서로 한다. 호스트가 먼저, 나머지는 확정 순.
     * 모임이 실제로 진행됐는지(진행중·종료)는 부르는 쪽이 모임 상태로 판단한다.
     */
    @Query(nativeQuery = true, value = """
            SELECT u.id AS userId, u.nickname AS nickname, (u.id = g.host_id) AS host
            FROM participations p
            JOIN check_ins c ON c.participation_id = p.id AND c.attendance IN ('ON_TIME', 'LATE')
            JOIN gatherings g ON g.id = p.gathering_id
            JOIN users u ON u.id = p.user_id
            WHERE p.gathering_id = :gatheringId
            ORDER BY (u.id = g.host_id) DESC, p.decided_at, p.id""")
    List<Attendee> findAttendees(Long gatheringId);

    interface WorkoutPartner {
        Long getUserId();

        String getNickname();

        long getWorkoutCount();

        Instant getLastWorkoutAt();
    }

    /**
     * 함께 운동한 사람: 함께한 모임 수와 마지막으로 함께한 모임의 시작 시각. 최근에 함께한 순.
     * nicknamePattern 은 ILIKE 패턴('%' 이면 전체).
     */
    @Query(nativeQuery = true, value = """
            SELECT other.user_id AS userId, u.nickname AS nickname,
                   COUNT(DISTINCT g.id) AS workoutCount, MAX(g.start_at) AS lastWorkoutAt
            FROM participations mine
            JOIN check_ins mc ON mc.participation_id = mine.id AND mc.attendance IN ('ON_TIME', 'LATE')
            JOIN gatherings g ON g.id = mine.gathering_id
                 AND g.status IN ('IN_PROGRESS', 'FINISHED')
            JOIN participations other ON other.gathering_id = g.id AND other.user_id <> mine.user_id
            JOIN check_ins oc ON oc.participation_id = other.id AND oc.attendance IN ('ON_TIME', 'LATE')
            JOIN users u ON u.id = other.user_id
            WHERE mine.user_id = :userId AND u.nickname ILIKE :nicknamePattern ESCAPE '\\'
            GROUP BY other.user_id, u.nickname
            ORDER BY MAX(g.start_at) DESC, other.user_id""")
    List<WorkoutPartner> findWorkoutPartners(Long userId, String nicknamePattern);

    interface AttendedCount {
        Long getUserId();

        long getCount();
    }

    /** 사람마다 참여 횟수('참여 n회'): 남이 연 모임에서 체크인(정상·지각)한 수. 내가 연 모임은 '개설 n회'로 따로 센다. */
    @Query("""
            select p.user.id as userId, count(c) as count
            from CheckIn c join c.participation p join p.gathering g
            where p.user.id in :userIds
              and c.attendance in (com.buddygo.buddygoserver.domain.checkin.enums.Attendance.ON_TIME,
                                   com.buddygo.buddygoserver.domain.checkin.enums.Attendance.LATE)
              and g.status in (com.buddygo.buddygoserver.domain.gathering.enums.GatheringStatus.IN_PROGRESS,
                               com.buddygo.buddygoserver.domain.gathering.enums.GatheringStatus.FINISHED)
              and g.host.id <> p.user.id
            group by p.user.id""")
    List<AttendedCount> countAttendedByUserIds(Collection<Long> userIds);
}
