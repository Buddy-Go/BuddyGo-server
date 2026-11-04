package com.buddygo.buddygoserver.domain.sport.gym.repository;

import com.buddygo.buddygoserver.domain.sport.gym.entity.GatheringGymPart;
import com.buddygo.buddygoserver.domain.sport.gym.entity.GatheringGymPartId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GatheringGymPartRepository extends JpaRepository<GatheringGymPart, GatheringGymPartId> {

    List<GatheringGymPart> findAllByIdGatheringId(Long gatheringId);
}
