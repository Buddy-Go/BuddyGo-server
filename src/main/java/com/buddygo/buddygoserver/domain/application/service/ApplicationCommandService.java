package com.buddygo.buddygoserver.domain.application.service;

import com.buddygo.buddygoserver.domain.application.dto.response.ApplicationResponse;
import com.buddygo.buddygoserver.domain.application.entity.Participation;
import com.buddygo.buddygoserver.domain.application.exception.ApplicationErrorType;
import com.buddygo.buddygoserver.domain.application.repository.ParticipationRepository;
import com.buddygo.buddygoserver.domain.gathering.config.GatheringProperties;
import com.buddygo.buddygoserver.domain.gathering.entity.Gathering;
import com.buddygo.buddygoserver.domain.gathering.exception.GatheringErrorType;
import com.buddygo.buddygoserver.domain.gathering.repository.GatheringRepository;
import com.buddygo.buddygoserver.domain.user.entity.User;
import com.buddygo.buddygoserver.domain.user.exception.UserErrorType;
import com.buddygo.buddygoserver.domain.user.repository.UserRepository;
import com.buddygo.buddygoserver.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * 신청·수락·거절·나가기. 모든 변경은 모임 행을 먼저 잠그고(findActiveByIdForUpdate) 참여 상태를 읽는다.
 * 모임 수정·삭제·취소도 같은 행을 잠그므로 확정 인원과 모임 상태 판단이 서로 어긋나지 않는다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ApplicationCommandService {

    private final GatheringRepository gatheringRepository;
    private final ParticipationRepository participationRepository;
    private final UserRepository userRepository;
    private final GatheringProperties gatheringProperties;
    private final Clock clock;

    /**
     * 참여 신청. 막는 경우는 모집중이 아님·본인이 호스트·성별 조건 불일치·이미 대기/확정뿐이다.
     * 기준이 맞지 않거나 다른 모임과 시간이 겹쳐도 막지 않고, 취소·거절·나간 뒤에도 다시 신청할 수 있다.
     */
    public ApplicationResponse apply(Long userId, Long gatheringId, String message) {
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
        // TODO 호스트에게 신청 알림 · 메이트 알림 (알림 도메인 구현 후)
        return ApplicationResponse.from(application);
    }

    private Gathering lockGathering(Long gatheringId) {
        return gatheringRepository.findActiveByIdForUpdate(gatheringId)
                .orElseThrow(() -> new CustomException(GatheringErrorType.NOT_FOUND));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
