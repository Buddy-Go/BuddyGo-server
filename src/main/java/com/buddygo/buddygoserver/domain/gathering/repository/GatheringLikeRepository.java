package com.buddygo.buddygoserver.domain.gathering.repository;

import com.buddygo.buddygoserver.domain.gathering.entity.GatheringLike;
import com.buddygo.buddygoserver.domain.gathering.entity.GatheringLikeId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.Set;

public interface GatheringLikeRepository extends JpaRepository<GatheringLike, GatheringLikeId> {

    /** 찜은 반복 요청이 정상 흐름이라 gathering_likes_pkey 충돌은 이미 찜한 것으로 보고 건너뛴다. */
    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO gathering_likes (user_id, gathering_id, created_at)
            VALUES (:userId, :gatheringId, :createdAt)
            ON CONFLICT (user_id, gathering_id) DO NOTHING""")
    int insertIgnoringDuplicate(Long userId, Long gatheringId, Instant createdAt);

    /** gatheringIds 중 이 사용자가 찜한 모임 */
    @Query("select l.id.gatheringId from GatheringLike l where l.id.userId = :userId and l.id.gatheringId in :gatheringIds")
    Set<Long> findLikedGatheringIds(Long userId, Collection<Long> gatheringIds);
}
