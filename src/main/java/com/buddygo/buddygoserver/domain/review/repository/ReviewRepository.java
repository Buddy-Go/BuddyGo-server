package com.buddygo.buddygoserver.domain.review.repository;

import com.buddygo.buddygoserver.domain.review.entity.Review;
import com.buddygo.buddygoserver.domain.review.enums.ReviewRating;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    /** 받은 평가 수 (프로필·호스트 카드의 '후기 n'). 아쉬움 평가도 센다. */
    long countByTargetId(Long targetId);

    /**
     * 받은 평가 목록 한 페이지: 최신순(작성 시각, id 내림차순)으로 커서 뒤부터. 평가자·모임을 함께 읽는다.
     *
     * @param ratings 보여줄 판정(남의 목록은 아쉬움을 뺀다)
     */
    @Query("""
            select r from Review r join fetch r.evaluator join fetch r.gathering
            where r.target.id = :targetId and r.rating in :ratings
              and (r.createdAt < :beforeCreatedAt or (r.createdAt = :beforeCreatedAt and r.id < :beforeId))
            order by r.createdAt desc, r.id desc""")
    List<Review> findReceived(Long targetId, Collection<ReviewRating> ratings,
                              Instant beforeCreatedAt, long beforeId, Limit limit);

    boolean existsByGatheringIdAndEvaluatorIdAndTargetId(Long gatheringId, Long evaluatorId, Long targetId);

    /** 이 모임에서 내가 이미 평가한 사람 */
    @Query("select r.target.id from Review r where r.gathering.id = :gatheringId and r.evaluator.id = :evaluatorId")
    Set<Long> findTargetIds(Long gatheringId, Long evaluatorId);

    interface PendingGathering {
        Long getGatheringId();

        String getTitle();

        Instant getStartAt();

        Instant getEndAt();

        long getRemainingCount();
    }

    /**
     * 평가 대기 모임: 내가 체크인(정상·지각)했고, 끝났고(진행중·종료이면서 종료 시각이 지남), 기한 전이며
     * 아직 평가하지 않은 다른 체크인한 사람이 남은 모임. 기한이 가까운 순.
     *
     * @param endedAfter 종료 시각이 이 시각보다 뒤인 모임만(= 지금 - 평가 기한)
     */
    @Query(nativeQuery = true, value = """
            SELECT * FROM (
                SELECT g.id AS gatheringId, g.title AS title, g.start_at AS startAt, g.end_at AS endAt,
                       (SELECT count(*)
                        FROM participations op
                        JOIN check_ins oc ON oc.participation_id = op.id AND oc.attendance IN ('ON_TIME', 'LATE')
                        WHERE op.gathering_id = g.id AND op.user_id <> mine.user_id
                          AND NOT EXISTS (SELECT 1 FROM reviews r
                                          WHERE r.gathering_id = g.id AND r.evaluator_id = mine.user_id
                                            AND r.target_id = op.user_id)) AS remainingCount
                FROM participations mine
                JOIN check_ins mc ON mc.participation_id = mine.id AND mc.attendance IN ('ON_TIME', 'LATE')
                JOIN gatherings g ON g.id = mine.gathering_id AND g.status IN ('IN_PROGRESS', 'FINISHED')
                WHERE mine.user_id = :userId AND g.end_at <= :now AND g.end_at > :endedAfter
            ) pending
            WHERE remainingCount > 0
            ORDER BY endAt, gatheringId""")
    List<PendingGathering> findPending(Long userId, Instant now, Instant endedAfter);
}
