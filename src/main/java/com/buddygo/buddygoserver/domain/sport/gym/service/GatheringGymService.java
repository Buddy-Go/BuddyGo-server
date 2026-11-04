package com.buddygo.buddygoserver.domain.sport.gym.service;

import com.buddygo.buddygoserver.domain.gathering.entity.Gathering;
import com.buddygo.buddygoserver.domain.sport.gym.criteria.GymCriteria;
import com.buddygo.buddygoserver.domain.sport.gym.dto.response.GatheringGymCriteriaResponse;
import com.buddygo.buddygoserver.domain.sport.gym.dto.response.GatheringGymCriteriaResponse.MatchResponse;
import com.buddygo.buddygoserver.domain.sport.gym.dto.response.GatheringGymCriteriaResponse.PartResponse;
import com.buddygo.buddygoserver.domain.sport.gym.entity.GatheringGymPart;
import com.buddygo.buddygoserver.domain.sport.gym.entity.UserGymRecord;
import com.buddygo.buddygoserver.domain.sport.gym.enums.BodyPart;
import com.buddygo.buddygoserver.domain.sport.gym.repository.GatheringGymPartRepository;
import com.buddygo.buddygoserver.domain.sport.gym.repository.UserGymRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
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

    private Map<BodyPart, UserGymRecord> hostRecordsToShow(Gathering gathering, boolean showMyRecord) {
        return showMyRecord ? recordsOf(gathering.getHost().getId()) : Map.of();
    }

    private Map<BodyPart, UserGymRecord> recordsOf(Long userId) {
        Map<BodyPart, UserGymRecord> records = new EnumMap<>(BodyPart.class);
        userGymRecordRepository.findAllByIdUserId(userId).forEach(r -> records.put(r.getBodyPart(), r));
        return records;
    }
}
