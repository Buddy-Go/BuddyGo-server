package com.buddygo.buddygoserver.domain.gathering.service;

import com.buddygo.buddygoserver.domain.application.entity.Participation;
import com.buddygo.buddygoserver.domain.application.enums.ParticipationStatus;
import com.buddygo.buddygoserver.domain.application.repository.ParticipationRepository;
import com.buddygo.buddygoserver.domain.gathering.config.GatheringProperties;
import com.buddygo.buddygoserver.domain.gathering.dto.response.GatheringDetailResponse;
import com.buddygo.buddygoserver.domain.gathering.dto.response.GatheringDetailResponse.CancellationResponse;
import com.buddygo.buddygoserver.domain.gathering.dto.response.GatheringDetailResponse.MemberResponse;
import com.buddygo.buddygoserver.domain.gathering.dto.response.GatheringDetailResponse.ViewerResponse;
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
import java.util.Comparator;
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
        boolean isHost = hostId.equals(viewerId);

        Optional<Participation> mine = isHost ? Optional.empty()
                : participationRepository.findActive(gatheringId, viewerId);
        ViewerRole role = isHost ? ViewerRole.HOST
                : mine.map(p -> p.getStatus() == ParticipationStatus.CONFIRMED ? ViewerRole.PARTICIPANT : ViewerRole.APPLICANT)
                .orElse(ViewerRole.NONE);
        ViewerResponse viewer = new ViewerResponse(role, mine.map(Participation::getId).orElse(null),
                gatheringLikeRepository.existsById(new GatheringLikeId(viewerId, gatheringId)));

        List<MemberResponse> members = canSeeMembers(gathering, role)
                ? confirmedMembers(gathering)
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

    /** 상세 화면과 같은 공개 규칙을 사용하는 확정 명단 전용 조회. */
    public List<MemberResponse> getParticipants(Long gatheringId, Long viewerId) {
        Gathering gathering = gatheringRepository.findWithHostAndPlace(gatheringId)
                .orElseThrow(() -> new CustomException(GatheringErrorType.NOT_FOUND));
        ViewerRole role = gathering.isHostedBy(viewerId) ? ViewerRole.HOST
                : participationRepository.findActive(gatheringId, viewerId)
                        .filter(Participation::isConfirmed)
                        .map(p -> ViewerRole.PARTICIPANT).orElse(ViewerRole.NONE);
        if (!canSeeMembers(gathering, role)) {
            throw new CustomException(GatheringErrorType.PARTICIPANTS_NOT_VISIBLE);
        }
        return confirmedMembers(gathering);
    }

    private List<MemberResponse> confirmedMembers(Gathering gathering) {
        return participationRepository.findConfirmedWithUser(gathering.getId()).stream()
                .map(p -> MemberResponse.of(p, gathering.getHost().getId()))
                .sorted(Comparator.comparing(MemberResponse::host).reversed())
                .toList();
    }

    private static boolean canSeeMembers(Gathering gathering, ViewerRole role) {
        return role == ViewerRole.HOST
                || (role == ViewerRole.PARTICIPANT && MEMBERS_VISIBLE_TO_PARTICIPANT.contains(gathering.getStatus()));
    }
}
