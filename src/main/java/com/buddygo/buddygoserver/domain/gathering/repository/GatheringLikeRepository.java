package com.buddygo.buddygoserver.domain.gathering.repository;

import com.buddygo.buddygoserver.domain.gathering.entity.GatheringLike;
import com.buddygo.buddygoserver.domain.gathering.entity.GatheringLikeId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GatheringLikeRepository extends JpaRepository<GatheringLike, GatheringLikeId> {
}
