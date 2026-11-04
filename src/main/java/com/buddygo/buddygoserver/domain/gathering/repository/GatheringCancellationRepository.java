package com.buddygo.buddygoserver.domain.gathering.repository;

import com.buddygo.buddygoserver.domain.gathering.entity.GatheringCancellation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GatheringCancellationRepository extends JpaRepository<GatheringCancellation, Long> {
}
