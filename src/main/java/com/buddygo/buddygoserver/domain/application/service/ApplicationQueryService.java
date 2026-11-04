package com.buddygo.buddygoserver.domain.application.service;

import com.buddygo.buddygoserver.domain.application.dto.response.ApplicantsResponse;
import com.buddygo.buddygoserver.domain.application.dto.response.ApplicantsResponse.ApplicantResponse;
import com.buddygo.buddygoserver.domain.application.dto.response.ApplicantsResponse.MemberResponse;
import com.buddygo.buddygoserver.domain.application.entity.Participation;
import com.buddygo.buddygoserver.domain.application.repository.ParticipationRepository;
import com.buddygo.buddygoserver.domain.gathering.entity.Gathering;
import com.buddygo.buddygoserver.domain.gathering.exception.GatheringErrorType;
import com.buddygo.buddygoserver.domain.gathering.repository.GatheringRepository;
import com.buddygo.buddygoserver.domain.sport.gym.dto.response.GymProfileResponse.GymRecordResponse;
import com.buddygo.buddygoserver.domain.sport.gym.service.ApplicationGymService;
import com.buddygo.buddygoserver.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ApplicationQueryService {

    private final GatheringRepository gatheringRepository;
    private final ParticipationRepository participationRepository;
    private final ApplicationGymService applicationGymService;

    /** 호스트용 참가자 관리. 신청자에게는 닉네임·매너온도·신청 시점 헬스 수준(없으면 미설정)·한마디가 보인다. */
    public ApplicantsResponse getApplicants(Long userId, Long gatheringId) {
        Gathering gathering = gatheringRepository.findActiveWithHostAndPlace(gatheringId)
                .orElseThrow(() -> new CustomException(GatheringErrorType.NOT_FOUND));
        if (!gathering.isHostedBy(userId)) {
            throw new CustomException(GatheringErrorType.NOT_HOST);
        }
        Long hostId = gathering.getHost().getId();

        List<MemberResponse> confirmed = participationRepository.findConfirmedWithUser(gatheringId).stream()
                .map(p -> MemberResponse.of(p, hostId))
                .toList();
        List<Participation> pending = participationRepository.findPendingWithUser(gatheringId);
        Map<Long, List<GymRecordResponse>> records = applicationGymService.recordsOf(gatheringId,
                pending.stream().map(Participation::getId).toList());
        return new ApplicantsResponse(confirmed, pending.stream()
                .map(p -> ApplicantResponse.of(p, records.get(p.getId())))
                .toList());
    }
}
