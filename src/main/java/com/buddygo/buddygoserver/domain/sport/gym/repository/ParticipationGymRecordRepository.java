package com.buddygo.buddygoserver.domain.sport.gym.repository;

import com.buddygo.buddygoserver.domain.sport.gym.entity.ParticipationGymRecord;
import com.buddygo.buddygoserver.domain.sport.gym.entity.ParticipationGymRecordId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ParticipationGymRecordRepository
        extends JpaRepository<ParticipationGymRecord, ParticipationGymRecordId> {

    List<ParticipationGymRecord> findAllByIdParticipationIdIn(Collection<Long> participationIds);
}
