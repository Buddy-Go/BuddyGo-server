package com.buddygo.buddygoserver.domain.checkin.service;

import com.buddygo.buddygoserver.domain.application.entity.Participation;
import com.buddygo.buddygoserver.domain.application.repository.ParticipationRepository;
import com.buddygo.buddygoserver.domain.checkin.config.CheckInProperties;
import com.buddygo.buddygoserver.domain.checkin.dto.response.CheckInStatusResponse;
import com.buddygo.buddygoserver.domain.checkin.dto.response.CheckInStatusResponse.MemberCheckIn;
import com.buddygo.buddygoserver.domain.checkin.dto.response.CheckInStatusResponse.MyCheckIn;
import com.buddygo.buddygoserver.domain.checkin.entity.CheckIn;
import com.buddygo.buddygoserver.domain.checkin.exception.CheckInErrorType;
import com.buddygo.buddygoserver.domain.checkin.repository.CheckInRepository;
import com.buddygo.buddygoserver.domain.gathering.entity.Gathering;
import com.buddygo.buddygoserver.domain.gathering.exception.GatheringErrorType;
import com.buddygo.buddygoserver.domain.gathering.repository.GatheringRepository;
import com.buddygo.buddygoserver.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CheckInQueryService {

    private final GatheringRepository gatheringRepository;
    private final ParticipationRepository participationRepository;
    private final CheckInRepository checkInRepository;
    private final CheckInProperties properties;

    /** 체크인 대상(호스트 + 확정 참가자)만 본다. */
    public CheckInStatusResponse getStatus(Long userId, Long gatheringId) {
        Gathering gathering = gatheringRepository.findById(gatheringId)
                .orElseThrow(() -> new CustomException(GatheringErrorType.NOT_FOUND));
        Participation mine = participationRepository.findActive(gatheringId, userId)
                .filter(Participation::isConfirmed)
                .orElseThrow(() -> new CustomException(CheckInErrorType.NOT_PARTICIPANT,
                        "확정된 참가자만 체크인 현황을 볼 수 있습니다."));
        Long hostId = gathering.getHost().getId();

        // 모임 상세의 확정 명단과 같은 공개 규칙
        List<MemberCheckIn> members = null;
        MyCheckIn me;
        if (userId.equals(hostId) || gathering.isRosterVisibleToParticipants()) {
            List<Participation> confirmed = participationRepository.findConfirmedWithUser(gatheringId);
            Map<Long, CheckIn> checkIns = checkInRepository.findAllById(
                            confirmed.stream().map(Participation::getId).toList()).stream()
                    .collect(Collectors.toMap(CheckIn::getId, Function.identity()));
            members = confirmed.stream()
                    .map(p -> MemberCheckIn.of(p, hostId, checkIns.get(p.getId())))
                    .toList();
            me = MyCheckIn.of(checkIns.get(mine.getId()));
        } else {
            me = MyCheckIn.of(checkInRepository.findById(mine.getId()).orElse(null));
        }

        return new CheckInStatusResponse(properties.openAt(gathering.getStartAt()),
                properties.lateFrom(gathering.getStartAt()), properties.noShowAt(gathering.getStartAt()),
                properties.radiusMeters(), me, members);
    }
}
