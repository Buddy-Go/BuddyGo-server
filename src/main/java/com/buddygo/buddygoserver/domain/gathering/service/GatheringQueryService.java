package com.buddygo.buddygoserver.domain.gathering.service;

import com.buddygo.buddygoserver.domain.application.entity.Participation;
import com.buddygo.buddygoserver.domain.application.enums.ParticipationStatus;
import com.buddygo.buddygoserver.domain.application.repository.ParticipationRepository;
import com.buddygo.buddygoserver.domain.gathering.config.GatheringProperties;
import com.buddygo.buddygoserver.domain.gathering.dto.response.GatheringDetailResponse;
import com.buddygo.buddygoserver.domain.gathering.dto.response.GatheringDetailResponse.CancellationResponse;
import com.buddygo.buddygoserver.domain.gathering.dto.response.GatheringDetailResponse.MemberResponse;
import com.buddygo.buddygoserver.domain.gathering.dto.response.GatheringDetailResponse.ViewerResponse;
import com.buddygo.buddygoserver.domain.gathering.dto.response.GatheringParticipantResponse;
import com.buddygo.buddygoserver.domain.gathering.entity.Gathering;
import com.buddygo.buddygoserver.domain.gathering.entity.GatheringLikeId;
import com.buddygo.buddygoserver.domain.gathering.enums.GatheringStatus;
import com.buddygo.buddygoserver.domain.gathering.enums.ViewerRole;
import com.buddygo.buddygoserver.domain.gathering.exception.GatheringErrorType;
import com.buddygo.buddygoserver.domain.gathering.repository.GatheringCancellationRepository;
import com.buddygo.buddygoserver.domain.gathering.repository.GatheringLikeRepository;
import com.buddygo.buddygoserver.domain.gathering.repository.GatheringRepository;
import com.buddygo.buddygoserver.domain.review.repository.ReviewRepository;
import com.buddygo.buddygoserver.domain.sport.gym.dto.response.GatheringGymCriteriaResponse;
import com.buddygo.buddygoserver.domain.sport.gym.service.GatheringGymService;
import com.buddygo.buddygoserver.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GatheringQueryService {

    /** 확정 참가자가 명단을 볼 수 있는 상태. 마감이 풀려 모집중으로 돌아가면 다시 가려진다. */
    private static final Set<GatheringStatus> MEMBERS_VISIBLE_TO_PARTICIPANT =
            EnumSet.of(GatheringStatus.CLOSED, GatheringStatus.IN_PROGRESS, GatheringStatus.FINISHED);

    private final GatheringRepository gatheringRepository;
    private final GatheringLikeRepository gatheringLikeRepository;
    private final GatheringCancellationRepository gatheringCancellationRepository;
    private final ParticipationRepository participationRepository;
    private final ReviewRepository reviewRepository;
    private final GatheringGymService gatheringGymService;
    private final GatheringProperties gatheringProperties;

    /** 보는 사람(호스트 / 확정 참가자 / 대기 신청자 / 그 외)에 따라 명단·판정·취소 사유가 달라진다. */
    public GatheringDetailResponse getDetail(Long gatheringId, Long viewerId) {
        Gathering gathering = gatheringRepository.findWithHostAndPlace(gatheringId)
                .orElseThrow(() -> new CustomException(GatheringErrorType.NOT_FOUND));
        Long hostId = gathering.getHost().getId();
        boolean isHost = gathering.isHostedBy(viewerId);

        Optional<Participation> mine = activeParticipation(gathering, viewerId);
        ViewerRole role = roleOf(isHost, mine);
        ViewerResponse viewer = new ViewerResponse(role, mine.map(Participation::getId).orElse(null),
                gatheringLikeRepository.existsById(new GatheringLikeId(viewerId, gatheringId)));

        List<MemberResponse> members = canSeeMembers(gathering, role)
                ? participationRepository.findConfirmedWithUser(gatheringId).stream()
                        .map(p -> MemberResponse.of(p, hostId)).toList()
                : null;

        // 호스트 본인 기록과 비교하는 것은 의미가 없어 판정하지 않는다
        GatheringGymCriteriaResponse gym = gatheringGymService.getCriteria(gatheringId, isHost ? null : viewerId);

        CancellationResponse cancellation = gathering.isCanceled()
                ? gatheringCancellationRepository.findById(gatheringId)
                        .map(c -> CancellationResponse.of(c, role == ViewerRole.HOST || role == ViewerRole.PARTICIPANT))
                        .orElse(null)
                : null;

        return GatheringDetailResponse.of(gathering, gatheringProperties.deadlineOf(gathering.getStartAt()),
                participationRepository.countByGatheringIdAndStatus(gatheringId, ParticipationStatus.CONFIRMED),
                isHost ? participationRepository.countByGatheringIdAndStatus(gatheringId, ParticipationStatus.PENDING) : null,
                reviewRepository.countByTargetId(hostId), gym, viewer, members, cancellation);
    }

    /** 상세 화면의 members 와 같은 공개 규칙을 쓰는 확정 명단 전용 조회. */
    public List<GatheringParticipantResponse> getParticipants(Long gatheringId, Long viewerId) {
        // 호스트 id 만 쓰므로 장소·호스트를 함께 읽지 않는다
        Gathering gathering = gatheringRepository.findById(gatheringId)
                .orElseThrow(() -> new CustomException(GatheringErrorType.NOT_FOUND));
        boolean isHost = gathering.isHostedBy(viewerId);
        if (!canSeeMembers(gathering, roleOf(isHost, activeParticipation(gathering, viewerId)))) {
            throw new CustomException(GatheringErrorType.PARTICIPANTS_NOT_VISIBLE);
        }
        Long hostId = gathering.getHost().getId();
        return participationRepository.findConfirmedWithUser(gatheringId).stream()
                .map(p -> GatheringParticipantResponse.of(p, hostId))
                .toList();
    }

    /** 보는 사람의 진행 중인 신청·참여(대기·확정). 호스트 본인은 신청자로 보지 않는다. */
    private Optional<Participation> activeParticipation(Gathering gathering, Long viewerId) {
        return gathering.isHostedBy(viewerId) ? Optional.empty()
                : participationRepository.findActive(gathering.getId(), viewerId);
    }

    private static ViewerRole roleOf(boolean isHost, Optional<Participation> mine) {
        if (isHost) {
            return ViewerRole.HOST;
        }
        return mine.map(p -> p.isConfirmed() ? ViewerRole.PARTICIPANT : ViewerRole.APPLICANT).orElse(ViewerRole.NONE);
    }

    private static boolean canSeeMembers(Gathering gathering, ViewerRole role) {
        return role == ViewerRole.HOST
                || (role == ViewerRole.PARTICIPANT && MEMBERS_VISIBLE_TO_PARTICIPANT.contains(gathering.getStatus()));
    }
}
