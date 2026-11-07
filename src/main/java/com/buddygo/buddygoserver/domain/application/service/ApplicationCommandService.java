package com.buddygo.buddygoserver.domain.application.service;

import com.buddygo.buddygoserver.domain.application.dto.response.ApplicationResponse;
import com.buddygo.buddygoserver.domain.application.entity.Participation;
import com.buddygo.buddygoserver.domain.application.enums.ParticipationStatus;
import com.buddygo.buddygoserver.domain.application.enums.RejectReason;
import com.buddygo.buddygoserver.domain.application.exception.ApplicationErrorType;
import com.buddygo.buddygoserver.domain.application.repository.ParticipationRepository;
import com.buddygo.buddygoserver.domain.gathering.config.GatheringProperties;
import com.buddygo.buddygoserver.domain.gathering.entity.Gathering;
import com.buddygo.buddygoserver.domain.gathering.entity.GatheringCancellation;
import com.buddygo.buddygoserver.domain.gathering.enums.GatheringStatus;
import com.buddygo.buddygoserver.domain.gathering.exception.GatheringErrorType;
import com.buddygo.buddygoserver.domain.gathering.repository.GatheringCancellationRepository;
import com.buddygo.buddygoserver.domain.gathering.repository.GatheringRepository;
import com.buddygo.buddygoserver.domain.sport.gym.dto.request.GymRecordRequest;
import com.buddygo.buddygoserver.domain.sport.gym.service.ApplicationGymService;
import com.buddygo.buddygoserver.domain.user.entity.User;
import com.buddygo.buddygoserver.domain.user.exception.UserErrorType;
import com.buddygo.buddygoserver.domain.user.repository.UserRepository;
import com.buddygo.buddygoserver.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * 신청·수락·거절·나가기. 모든 변경은 모임 행을 먼저 잠그고(findByIdForUpdate) 참여 상태를 읽는다.
 * 모임 수정·삭제·취소도 같은 행을 잠그므로 확정 인원과 모임 상태 판단이 서로 어긋나지 않는다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ApplicationCommandService {

    private final GatheringRepository gatheringRepository;
    private final GatheringCancellationRepository gatheringCancellationRepository;
    private final ParticipationRepository participationRepository;
    private final UserRepository userRepository;
    private final ApplicationGymService applicationGymService;
    private final GatheringProperties gatheringProperties;
    private final Clock clock;

    /**
     * 참여 신청. 막는 경우는 모집중이 아님·본인이 호스트·성별 조건 불일치·이미 대기/확정뿐이다.
     * 기준이 맞지 않거나 다른 모임과 시간이 겹쳐도 막지 않고, 취소·거절·나간 뒤에도 다시 신청할 수 있다.
     * 헬스 수준은 신청에 스냅샷한다(ApplicationGymService.snapshot).
     */
    public ApplicationResponse apply(Long userId, Long gatheringId, String message, List<GymRecordRequest> gymRecords) {
        Instant now = Instant.now(clock);
        Gathering gathering = lockGathering(gatheringId);
        if (!gathering.isRecruiting(now, gatheringProperties.deadlineOf(gathering.getStartAt()))) {
            throw new CustomException(GatheringErrorType.NOT_RECRUITING);
        }
        if (gathering.isHostedBy(userId)) {
            throw new CustomException(ApplicationErrorType.HOST_CANNOT_APPLY);
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(UserErrorType.NOT_FOUND));
        if (!gathering.getGenderRule().allows(user.getGender())) {
            throw new CustomException(ApplicationErrorType.GENDER_RULE_MISMATCH);
        }
        if (participationRepository.findActive(gatheringId, userId).isPresent()) {
            throw new CustomException(ApplicationErrorType.ALREADY_APPLIED);
        }
        // 사전 검사를 지나도 uk_participations_active 에 걸리면 AP002 로 바뀐다
        Participation application = participationRepository.saveAndFlush(
                Participation.apply(gathering, user, blankToNull(message), now));
        applicationGymService.snapshot(application, gymRecords);
        // TODO 호스트에게 신청 알림 · 메이트 알림 (알림 도메인 구현 후)
        return ApplicationResponse.from(application);
    }

    /** 대기 중인 내 신청을 취소한다. 확정된 뒤에는 취소가 아니라 나가기다. */
    public void cancelMine(Long userId, Long gatheringId) {
        lockGathering(gatheringId);
        Participation application = participationRepository.findActive(gatheringId, userId)
                .orElseThrow(() -> new CustomException(ApplicationErrorType.NOT_FOUND));
        if (!application.isPending()) {
            throw new CustomException(ApplicationErrorType.NOT_PENDING);
        }
        participationRepository.deleteWithChildren(application.getId());
    }

    /**
     * 수락. 정원(호스트 포함 확정 인원)이 차면 즉시 마감하고 남은 대기 신청을 모두 자동 거절한다.
     */
    public ApplicationResponse accept(Long userId, Long gatheringId, Long applicationId) {
        Instant now = Instant.now(clock);
        Gathering gathering = lockGatheringAsHost(gatheringId, userId);
        Participation application = pendingApplicationOf(gathering, applicationId);
        if (!gathering.isRecruiting(now, gatheringProperties.deadlineOf(gathering.getStartAt()))) {
            throw new CustomException(GatheringErrorType.NOT_RECRUITING);
        }
        long confirmed = participationRepository.countByGatheringIdAndStatus(gatheringId, ParticipationStatus.CONFIRMED);
        if (confirmed >= gathering.getCapacity()) {
            throw new CustomException(ApplicationErrorType.GATHERING_FULL);
        }

        application.confirm(now);
        if (confirmed + 1 == gathering.getCapacity()) {
            gathering.close();
            // 벌크 UPDATE 전에 위의 수락이 flush 되므로(flushAutomatically) 수락한 신청은 거절되지 않는다
            participationRepository.rejectAllPending(gatheringId, RejectReason.MEETING_FULL, now);
            // TODO 마감 알림(호스트 + 확정 참가자) · 자동 거절 알림
        }
        // TODO 수락 알림
        return ApplicationResponse.from(application);
    }

    public ApplicationResponse reject(Long userId, Long gatheringId, Long applicationId) {
        Gathering gathering = lockGatheringAsHost(gatheringId, userId);
        Participation application = pendingApplicationOf(gathering, applicationId);
        application.reject(RejectReason.BY_HOST, Instant.now(clock));
        // TODO 거절 알림
        return ApplicationResponse.from(application);
    }

    /**
     * 확정 참가자 나가기. 호스트는 나갈 수 없고(취소를 사용), 진행중이 되면 나가기 대신 노쇼 처리된다.
     * <ul>
     *   <li>모집중: 확정 인원만 줄어든다. 매너온도 영향 없음</li>
     *   <li>마감 · 데드라인 전: 마감이 풀려 다시 모집한다</li>
     *   <li>마감 · 데드라인 후: 모임 전체가 취소된다(방폭). 남은 사람은 페널티 없음</li>
     * </ul>
     */
    public ApplicationResponse leave(Long userId, Long gatheringId) {
        Instant now = Instant.now(clock);
        Gathering gathering = lockGathering(gatheringId);
        if (gathering.isHostedBy(userId)) {
            throw new CustomException(ApplicationErrorType.HOST_CANNOT_LEAVE);
        }
        if (gathering.isCanceled()) {
            throw new CustomException(GatheringErrorType.ALREADY_CANCELED);
        }
        if (gathering.hasStarted(now)) {
            throw new CustomException(GatheringErrorType.ALREADY_STARTED);
        }
        Participation participation = participationRepository.findActive(gatheringId, userId)
                .filter(Participation::isConfirmed)
                .orElseThrow(() -> new CustomException(ApplicationErrorType.NOT_PARTICIPANT));

        participation.leave(now);
        if (gathering.getStatus() == GatheringStatus.CLOSED) {
            if (now.isBefore(gatheringProperties.deadlineOf(gathering.getStartAt()))) {
                gathering.reopen();
                // TODO 나간 사람 매너온도 하락 · 마감 풀림 알림(호스트 + 남은 확정 참가자)
            } else {
                gathering.cancel();
                gatheringCancellationRepository.save(GatheringCancellation.byMemberLeft(gathering, now));
                // TODO 나간 사람 매너온도 하락(데드라인 전보다 크게) · 방폭 알림(호스트 + 남은 확정 참가자)
            }
        }
        return ApplicationResponse.from(participation);
    }

    private Gathering lockGatheringAsHost(Long gatheringId, Long userId) {
        Gathering gathering = lockGathering(gatheringId);
        if (!gathering.isHostedBy(userId)) {
            throw new CustomException(GatheringErrorType.NOT_HOST);
        }
        return gathering;
    }

    /** 다른 모임의 신청 ID 면 없는 신청으로 본다. */
    private Participation pendingApplicationOf(Gathering gathering, Long applicationId) {
        Participation application = participationRepository.findById(applicationId)
                .filter(p -> p.getGathering().getId().equals(gathering.getId()))
                .orElseThrow(() -> new CustomException(ApplicationErrorType.NOT_FOUND));
        if (!application.isPending()) {
            throw new CustomException(ApplicationErrorType.NOT_PENDING);
        }
        return application;
    }

    private Gathering lockGathering(Long gatheringId) {
        return gatheringRepository.findByIdForUpdate(gatheringId)
                .orElseThrow(() -> new CustomException(GatheringErrorType.NOT_FOUND));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
