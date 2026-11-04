package com.buddygo.buddygoserver.domain.sport.gym.service;

import com.buddygo.buddygoserver.domain.sport.gym.dto.request.GymRecordRequest;
import com.buddygo.buddygoserver.domain.sport.gym.dto.request.GymRecordsRequest;
import com.buddygo.buddygoserver.domain.sport.gym.dto.response.GymProfileResponse;
import com.buddygo.buddygoserver.domain.sport.gym.entity.UserGymRecord;
import com.buddygo.buddygoserver.domain.sport.gym.enums.BodyPart;
import com.buddygo.buddygoserver.domain.sport.gym.repository.UserGymRecordRepository;
import com.buddygo.buddygoserver.domain.user.entity.User;
import com.buddygo.buddygoserver.domain.user.exception.UserErrorType;
import com.buddygo.buddygoserver.domain.user.repository.UserRepository;
import com.buddygo.buddygoserver.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional
public class GymRecordCommandService {

    private final UserRepository userRepository;
    private final UserGymRecordRepository userGymRecordRepository;

    /**
     * 보낸 부위로 기록 전체를 바꾼다. 같은 키를 지웠다 다시 넣지 않고
     * 있는 부위는 고치고, 새 부위는 넣고, 빠진 부위만 지운다.
     */
    public GymProfileResponse replaceMyRecords(Long userId, GymRecordsRequest request) {
        // 같은 사용자의 동시 저장이 같은 (user_id, body_part) 를 함께 INSERT 하지 않도록 사용자 행을 잠근다
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new CustomException(UserErrorType.NOT_FOUND));

        Map<BodyPart, UserGymRecord> existing = new EnumMap<>(BodyPart.class);
        userGymRecordRepository.findAllByIdUserId(userId).forEach(r -> existing.put(r.getBodyPart(), r));

        List<UserGymRecord> result = new ArrayList<>();
        for (GymRecordRequest req : request.records()) {
            UserGymRecord record = existing.remove(req.bodyPart());
            if (record == null) {
                record = userGymRecordRepository.save(
                        UserGymRecord.of(user, req.bodyPart(), req.recordValue(), req.armLevel()));
            } else {
                record.update(req.recordValue(), req.armLevel());
            }
            result.add(record);
        }
        userGymRecordRepository.deleteAll(existing.values());
        return GymProfileResponse.from(result);
    }
}
