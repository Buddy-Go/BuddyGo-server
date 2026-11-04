package com.buddygo.buddygoserver.domain.sport.gym.repository;

import com.buddygo.buddygoserver.domain.sport.gym.entity.UserGymRecord;
import com.buddygo.buddygoserver.domain.sport.gym.entity.UserGymRecordId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface UserGymRecordRepository extends JpaRepository<UserGymRecord, UserGymRecordId> {

    List<UserGymRecord> findAllByIdUserId(Long userId);

    List<UserGymRecord> findAllByIdUserIdIn(Collection<Long> userIds);
}
