package com.buddygo.buddygoserver.domain.sport.gym.service;

import com.buddygo.buddygoserver.domain.gathering.entity.Gathering;
import com.buddygo.buddygoserver.domain.sport.gym.criteria.GymCriteria;
import com.buddygo.buddygoserver.domain.sport.gym.dto.response.GatheringGymCriteriaResponse;
import com.buddygo.buddygoserver.domain.sport.gym.dto.response.GatheringGymCriteriaResponse.MatchResponse;
import com.buddygo.buddygoserver.domain.sport.gym.dto.response.GatheringGymCriteriaResponse.PartResponse;
import com.buddygo.buddygoserver.domain.sport.gym.dto.response.GymProfileResponse.GymRecordResponse;
import com.buddygo.buddygoserver.domain.sport.gym.entity.GatheringGymPart;
import com.buddygo.buddygoserver.domain.sport.gym.entity.UserGymRecord;
import com.buddygo.buddygoserver.domain.sport.gym.enums.ArmLevel;
import com.buddygo.buddygoserver.domain.sport.gym.enums.BodyPart;
import com.buddygo.buddygoserver.domain.sport.gym.repository.GatheringGymPartRepository;
import com.buddygo.buddygoserver.domain.sport.gym.repository.UserGymRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 헬스 모임의 기준(운동 부위 + 호스트 기록 스냅샷). 모임 도메인은 종목을 모르고 이 서비스를 부른다. */
@Service
@RequiredArgsConstructor
@Transactional
public class GatheringGymService {

    private final GatheringGymPartRepository gatheringGymPartRepository;
    private final UserGymRecordRepository userGymRecordRepository;
    private final GymCriteria gymCriteria;

    /**
     * 고른 부위를 저장한다. '내 기록 표시'를 켜면 지금의 호스트 기록을 복사해 두고, 이후 프로필을 바꿔도 그대로다.
     * 끄거나 기록이 없는 부위는 값 없이 부위만 저장한다.
     */
    public void saveCriteria(Gathering gathering, Collection<BodyPart> parts, boolean showMyRecord) {
        Map<BodyPart, UserGymRecord> hostRecords = hostRecordsToShow(gathering, showMyRecord);
        gatheringGymPartRepository.saveAll(parts.stream()
                .map(part -> {
                    UserGymRecord record = hostRecords.get(part);
                    return GatheringGymPart.of(gathering, part,
                            record == null ? null : record.getRecordValue(), record == null ? null : record.getArmLevel());
                })
                .toList());
    }

    /**
     * 부위 기준을 통째로 바꾼다. 같은 키를 지웠다 다시 넣지 않고(Hibernate 는 INSERT 를 DELETE 보다 먼저 내보낸다)
     * 남는 부위는 고치고, 새 부위는 넣고, 빠진 부위만 지운다.
     */
    public void replaceCriteria(Gathering gathering, Collection<BodyPart> parts, boolean showMyRecord) {
        Map<BodyPart, UserGymRecord> hostRecords = hostRecordsToShow(gathering, showMyRecord);
        Map<BodyPart, GatheringGymPart> existing = new EnumMap<>(BodyPart.class);
        gatheringGymPartRepository.findAllByIdGatheringId(gathering.getId()).forEach(p -> existing.put(p.getBodyPart(), p));

        for (BodyPart part : parts) {
            UserGymRecord record = hostRecords.get(part);
            BigDecimal recordValue = record == null ? null : record.getRecordValue();
            ArmLevel armLevel = record == null ? null : record.getArmLevel();
            GatheringGymPart current = existing.remove(part);
            if (current == null) {
                gatheringGymPartRepository.save(GatheringGymPart.of(gathering, part, recordValue, armLevel));
            } else {
                current.update(recordValue, armLevel);
            }
        }
        gatheringGymPartRepository.deleteAll(existing.values());
    }

    /**
     * @param judgeFor 내 기록과 비교받을 사용자. null 이면(호스트 본인) 부위별 비교와 판정을 하지 않는다.
     */
    @Transactional(readOnly = true)
    public GatheringGymCriteriaResponse getCriteria(Long gatheringId, Long judgeFor) {
        List<GatheringGymPart> criteria = gatheringGymPartRepository.findAllByIdGatheringId(gatheringId).stream()
                .sorted(Comparator.comparing(GatheringGymPart::getBodyPart))
                .toList();
        Map<BodyPart, UserGymRecord> mine = judgeFor == null ? Map.of() : recordsOf(judgeFor);
        List<PartResponse> parts = criteria.stream()
                .map(part -> toPartResponse(part, mine.get(part.getBodyPart()), judgeFor != null))
                .toList();
        MatchResponse match = judgeFor == null ? null : MatchResponse.from(gymCriteria.judge(criteria, mine));
        return new GatheringGymCriteriaResponse(parts, match);
    }

    private PartResponse toPartResponse(GatheringGymPart part, UserGymRecord mine, boolean compare) {
        BodyPart bodyPart = part.getBodyPart();
        GymCriteria.Range range = part.hasRecord() && bodyPart.getRecordType() != BodyPart.RecordType.LEVEL
                ? gymCriteria.rangeOf(bodyPart, part.getRecordValue())
                : null;
        return PartResponse.of(bodyPart, range, part.getArmLevel(), mine,
                compare ? gymCriteria.compare(part, mine) : null);
    }

    /**
     * 신청자들의 헬스 기록 중 모임 부위만 골라 사용자별로 돌려준다. 기록이 없는 부위는 recorded=false('미설정')다.
     * 신청자 수만큼 조회하지 않도록 한 번에 읽는다.
     */
    @Transactional(readOnly = true)
    public Map<Long, List<GymRecordResponse>> recordsForCriteria(Long gatheringId, Collection<Long> userIds) {
        List<BodyPart> parts = gatheringGymPartRepository.findAllByIdGatheringId(gatheringId).stream()
                .map(GatheringGymPart::getBodyPart)
                .sorted()
                .toList();
        Map<Long, Map<BodyPart, UserGymRecord>> byUser = new HashMap<>();
        if (!userIds.isEmpty()) {
            userGymRecordRepository.findAllByIdUserIdIn(userIds).forEach(r -> byUser
                    .computeIfAbsent(r.getId().getUserId(), id -> new EnumMap<>(BodyPart.class))
                    .put(r.getBodyPart(), r));
        }
        Map<Long, List<GymRecordResponse>> result = new HashMap<>();
        for (Long userId : userIds) {
            Map<BodyPart, UserGymRecord> records = byUser.getOrDefault(userId, Map.of());
            result.put(userId, parts.stream().map(part -> GymRecordResponse.of(part, records.get(part))).toList());
        }
        return result;
    }

    private Map<BodyPart, UserGymRecord> hostRecordsToShow(Gathering gathering, boolean showMyRecord) {
        return showMyRecord ? recordsOf(gathering.getHost().getId()) : Map.of();
    }

    private Map<BodyPart, UserGymRecord> recordsOf(Long userId) {
        Map<BodyPart, UserGymRecord> records = new EnumMap<>(BodyPart.class);
        userGymRecordRepository.findAllByIdUserId(userId).forEach(r -> records.put(r.getBodyPart(), r));
        return records;
    }
}
