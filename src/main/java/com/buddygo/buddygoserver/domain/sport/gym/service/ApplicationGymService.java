package com.buddygo.buddygoserver.domain.sport.gym.service;

import com.buddygo.buddygoserver.domain.application.entity.Participation;
import com.buddygo.buddygoserver.domain.application.exception.ApplicationErrorType;
import com.buddygo.buddygoserver.domain.sport.gym.dto.request.GymRecordRequest;
import com.buddygo.buddygoserver.domain.sport.gym.dto.response.GymProfileResponse.GymRecordResponse;
import com.buddygo.buddygoserver.domain.sport.gym.entity.GatheringGymPart;
import com.buddygo.buddygoserver.domain.sport.gym.entity.ParticipationGymRecord;
import com.buddygo.buddygoserver.domain.sport.gym.entity.UserGymRecord;
import com.buddygo.buddygoserver.domain.sport.gym.enums.BodyPart;
import com.buddygo.buddygoserver.domain.sport.gym.repository.GatheringGymPartRepository;
import com.buddygo.buddygoserver.domain.sport.gym.repository.ParticipationGymRecordRepository;
import com.buddygo.buddygoserver.domain.sport.gym.repository.UserGymRecordRepository;
import com.buddygo.buddygoserver.domain.user.entity.User;
import com.buddygo.buddygoserver.domain.user.exception.UserErrorType;
import com.buddygo.buddygoserver.domain.user.repository.UserRepository;
import com.buddygo.buddygoserver.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** 신청자의 헬스 수준. 신청 시점 값을 신청에 스냅샷해 호스트에게 보여준다 (규칙 정의서 5장). */
@Service
@RequiredArgsConstructor
@Transactional
public class ApplicationGymService {

    private final GatheringGymPartRepository gatheringGymPartRepository;
    private final ParticipationGymRecordRepository participationGymRecordRepository;
    private final UserGymRecordRepository userGymRecordRepository;
    private final UserRepository userRepository;

    /**
     * 신청에 헬스 수준을 스냅샷한다. 모임 부위만 받는다.
     * <ul>
     *   <li>requested 가 null: 지금 프로필 기록(모임 부위만)을 그대로 복사한다. 프로필은 바꾸지 않는다.</li>
     *   <li>requested 가 있으면: 그 값을 복사하고 보낸 부위는 프로필에도 반영한다. 보내지 않은 부위는 미설정이다.</li>
     * </ul>
     */
    public void snapshot(Participation application, List<GymRecordRequest> requested) {
        Long gatheringId = application.getGathering().getId();
        Long userId = application.getUser().getId();
        Set<BodyPart> parts = gatheringGymPartRepository.findAllByIdGatheringId(gatheringId).stream()
                .map(GatheringGymPart::getBodyPart)
                .collect(Collectors.toSet());

        if (requested == null) {
            userGymRecordRepository.findAllByIdUserId(userId).stream()
                    .filter(r -> parts.contains(r.getBodyPart()))
                    .forEach(r -> participationGymRecordRepository.save(ParticipationGymRecord.of(
                            application, r.getBodyPart(), r.getRecordValue(), r.getArmLevel())));
            return;
        }
        if (requested.stream().anyMatch(r -> !parts.contains(r.bodyPart()))) {
            throw new CustomException(ApplicationErrorType.GYM_PART_NOT_IN_GATHERING);
        }
        requested.forEach(r -> participationGymRecordRepository.save(ParticipationGymRecord.of(
                application, r.bodyPart(), r.recordValue(), r.armLevel())));
        mergeIntoProfile(userId, requested);
    }

    /** 신청별 헬스 수준(모임 부위 순서). 스냅샷이 없는 부위는 recorded=false('미설정')다. */
    @Transactional(readOnly = true)
    public Map<Long, List<GymRecordResponse>> recordsOf(Long gatheringId, Collection<Long> participationIds) {
        List<BodyPart> parts = gatheringGymPartRepository.findAllByIdGatheringId(gatheringId).stream()
                .map(GatheringGymPart::getBodyPart)
                .sorted()
                .toList();
        Map<Long, Map<BodyPart, ParticipationGymRecord>> byApplication = new HashMap<>();
        if (!participationIds.isEmpty()) {
            participationGymRecordRepository.findAllByIdParticipationIdIn(participationIds).forEach(r -> byApplication
                    .computeIfAbsent(r.getId().getParticipationId(), id -> new EnumMap<>(BodyPart.class))
                    .put(r.getBodyPart(), r));
        }
        Map<Long, List<GymRecordResponse>> result = new HashMap<>();
        for (Long participationId : participationIds) {
            Map<BodyPart, ParticipationGymRecord> records = byApplication.getOrDefault(participationId, Map.of());
            result.put(participationId, parts.stream().map(part -> toResponse(part, records.get(part))).toList());
        }
        return result;
    }

    /** 보낸 부위만 프로필에 넣거나 고친다. 보내지 않은 프로필 부위는 그대로 둔다. */
    private void mergeIntoProfile(Long userId, List<GymRecordRequest> requested) {
        // 같은 사용자의 동시 저장이 같은 (user_id, body_part) 를 함께 INSERT 하지 않도록 사용자 행을 잠근다
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new CustomException(UserErrorType.NOT_FOUND));
        Map<BodyPart, UserGymRecord> profile = new EnumMap<>(BodyPart.class);
        userGymRecordRepository.findAllByIdUserId(userId).forEach(r -> profile.put(r.getBodyPart(), r));
        for (GymRecordRequest r : requested) {
            UserGymRecord current = profile.get(r.bodyPart());
            if (current == null) {
                userGymRecordRepository.save(UserGymRecord.of(user, r.bodyPart(), r.recordValue(), r.armLevel()));
            } else {
                current.update(r.recordValue(), r.armLevel());
            }
        }
    }

    private static GymRecordResponse toResponse(BodyPart part, ParticipationGymRecord record) {
        return record == null
                ? new GymRecordResponse(part, false, null, null)
                : new GymRecordResponse(part, true, record.getRecordValue(), record.getArmLevel());
    }
}
