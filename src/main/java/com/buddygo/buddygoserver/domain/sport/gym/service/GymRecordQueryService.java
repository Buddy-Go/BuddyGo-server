package com.buddygo.buddygoserver.domain.sport.gym.service;

import com.buddygo.buddygoserver.domain.sport.gym.criteria.GymCriteria;
import com.buddygo.buddygoserver.domain.sport.gym.dto.response.GymProfileResponse;
import com.buddygo.buddygoserver.domain.sport.gym.repository.UserGymRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GymRecordQueryService {

    private final UserGymRecordRepository userGymRecordRepository;
    private final GymCriteria gymCriteria;

    public GymProfileResponse getMyRecords(Long userId) {
        return GymProfileResponse.from(userGymRecordRepository.findAllByIdUserId(userId), gymCriteria);
    }
}
