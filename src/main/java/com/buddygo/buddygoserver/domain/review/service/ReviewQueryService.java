package com.buddygo.buddygoserver.domain.review.service;

import com.buddygo.buddygoserver.domain.review.config.ReviewProperties;
import com.buddygo.buddygoserver.domain.review.dto.response.PendingReviewsResponse;
import com.buddygo.buddygoserver.domain.review.dto.response.ReviewTagsResponse;
import com.buddygo.buddygoserver.domain.review.dto.response.ReviewTargetsResponse;
import com.buddygo.buddygoserver.domain.review.dto.response.ReviewTargetsResponse.TargetResponse;
import com.buddygo.buddygoserver.domain.review.repository.ReviewRepository;
import com.buddygo.buddygoserver.domain.review.service.ReviewEligibility.Context;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewQueryService {

    private final ReviewRepository reviewRepository;
    private final ReviewEligibility eligibility;
    private final ReviewProperties reviewProperties;
    private final Clock clock;

    /** 평가할 사람이 남아 있고 기한 전인 모임. 기한이 가까운 순. */
    public PendingReviewsResponse getPending(Long userId) {
        Instant now = Instant.now(clock);
        return PendingReviewsResponse.of(
                reviewRepository.findPending(userId, now, now.minus(reviewProperties.deadlineAfterEnd())),
                reviewProperties::deadlineOf);
    }

    /** 나를 뺀 체크인한 사람들과 내가 평가했는지. 기한이 지나도 볼 수 있다(reviewable=false). */
    public ReviewTargetsResponse getTargets(Long userId, Long gatheringId) {
        Instant now = Instant.now(clock);
        Context context = eligibility.require(userId, gatheringId, now);
        Set<Long> reviewed = reviewRepository.findTargetIds(gatheringId, userId);
        return new ReviewTargetsResponse(gatheringId, context.deadline(), context.isOpen(now),
                context.attendees().stream()
                        .filter(a -> !a.getUserId().equals(userId))
                        .map(a -> new TargetResponse(a.getUserId(), a.getNickname(), a.getHost(),
                                reviewed.contains(a.getUserId())))
                        .toList());
    }

    public ReviewTagsResponse getTags() {
        return ReviewTagsResponse.all();
    }
}
