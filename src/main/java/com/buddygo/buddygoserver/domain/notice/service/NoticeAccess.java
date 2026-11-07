package com.buddygo.buddygoserver.domain.notice.service;

import com.buddygo.buddygoserver.domain.application.entity.Participation;
import com.buddygo.buddygoserver.domain.application.repository.ParticipationRepository;
import com.buddygo.buddygoserver.domain.gathering.config.GatheringProperties;
import com.buddygo.buddygoserver.domain.gathering.entity.Gathering;
import com.buddygo.buddygoserver.domain.gathering.enums.GatheringStatus;
import com.buddygo.buddygoserver.domain.gathering.exception.GatheringErrorType;
import com.buddygo.buddygoserver.domain.gathering.repository.GatheringRepository;
import com.buddygo.buddygoserver.domain.notice.entity.Notice;
import com.buddygo.buddygoserver.domain.notice.exception.NoticeErrorType;
import com.buddygo.buddygoserver.domain.notice.repository.NoticeRepository;
import com.buddygo.buddygoserver.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

/** 서비스 트랜잭션 안에서 사용한다. 쓰기는 모임 행을 먼저 잠가 나가기·취소·종료와 직렬화한다. */
@Component
@RequiredArgsConstructor
public class NoticeAccess {
    private final GatheringRepository gatheringRepository;
    private final ParticipationRepository participationRepository;
    private final NoticeRepository noticeRepository;
    private final GatheringProperties gatheringProperties;
    private final Clock clock;

    public Gathering read(Long userId, Long gatheringId) {
        Gathering g = gatheringRepository.findWithHostAndPlace(gatheringId)
                .orElseThrow(() -> new CustomException(GatheringErrorType.NOT_FOUND));
        requireMember(userId, g);
        return g;
    }

    public Gathering write(Long userId, Long gatheringId, boolean hostOnly) {
        Gathering g = gatheringRepository.findByIdForUpdate(gatheringId)
                .orElseThrow(() -> new CustomException(GatheringErrorType.NOT_FOUND));
        if (hostOnly && !g.isHostedBy(userId)) { throw new CustomException(GatheringErrorType.NOT_HOST); }
        requireMember(userId, g);
        Instant now = Instant.now(clock);
        if (g.isCanceled() || g.getStatus() == GatheringStatus.FINISHED || !now.isBefore(g.getEndAt())
                || g.isUnfilledAtDeadline(now, gatheringProperties.deadlineOf(g.getStartAt()))) {
            throw new CustomException(NoticeErrorType.READ_ONLY);
        }
        return g;
    }

    public Notice notice(Long gatheringId, Long noticeId) {
        return noticeRepository.findByIdAndGatheringId(noticeId, gatheringId)
                .orElseThrow(() -> new CustomException(NoticeErrorType.NOT_FOUND));
    }

    private void requireMember(Long userId, Gathering g) {
        if (!g.isHostedBy(userId) && !participationRepository.findActive(g.getId(), userId)
                .map(Participation::isConfirmed).orElse(false)) {
            throw new CustomException(NoticeErrorType.ACCESS_FORBIDDEN);
        }
    }
}
