package com.buddygo.buddygoserver.domain.review.service;

import com.buddygo.buddygoserver.domain.checkin.repository.CheckInRepository;
import com.buddygo.buddygoserver.domain.checkin.repository.CheckInRepository.Attendee;
import com.buddygo.buddygoserver.domain.gathering.entity.Gathering;
import com.buddygo.buddygoserver.domain.gathering.exception.GatheringErrorType;
import com.buddygo.buddygoserver.domain.gathering.repository.GatheringRepository;
import com.buddygo.buddygoserver.domain.review.config.ReviewProperties;
import com.buddygo.buddygoserver.domain.review.exception.ReviewErrorType;
import com.buddygo.buddygoserver.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * 평가 자격(서비스 규칙 정의서 11장): 끝난 모임에서 체크인(정상·지각)한 사람끼리, 호스트를 포함해 서로 평가한다.
 * 노쇼한 사람은 평가하지도 받지도 않는다. 기한은 제출 때만 막고 조회는 기한이 지나도 된다.
 */
@Component
@RequiredArgsConstructor
class ReviewEligibility {

    private final GatheringRepository gatheringRepository;
    private final CheckInRepository checkInRepository;
    private final ReviewProperties reviewProperties;

    /** 모임이 끝났고 내가 체크인한 사람이어야 한다. 아니면 R001. */
    Context require(Long userId, Long gatheringId, Instant now) {
        Gathering gathering = gatheringRepository.findById(gatheringId)
                .orElseThrow(() -> new CustomException(GatheringErrorType.NOT_FOUND));
        if (!gathering.hasEnded(now)) {
            throw new CustomException(ReviewErrorType.NOT_AVAILABLE);
        }
        List<Attendee> attendees = checkInRepository.findAttendees(gatheringId);
        if (attendees.stream().noneMatch(a -> a.getUserId().equals(userId))) {
            throw new CustomException(ReviewErrorType.NOT_AVAILABLE);
        }
        return new Context(gathering, attendees, reviewProperties.deadlineOf(gathering.getEndAt()));
    }

    /** @param deadline 이 시각부터는 평가할 수 없다(반열린 구간) */
    record Context(Gathering gathering, List<Attendee> attendees, Instant deadline) {

        boolean isOpen(Instant now) {
            return now.isBefore(deadline);
        }

        boolean isAttendee(Long userId) {
            return attendees.stream().anyMatch(a -> a.getUserId().equals(userId));
        }
    }
}
