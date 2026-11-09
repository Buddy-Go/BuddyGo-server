package com.buddygo.buddygoserver.domain.review.service;

import com.buddygo.buddygoserver.domain.review.config.ReviewProperties;
import com.buddygo.buddygoserver.domain.review.dto.response.PendingReviewsResponse;
import com.buddygo.buddygoserver.domain.review.dto.response.ReceivedReviewResponse;
import com.buddygo.buddygoserver.domain.review.dto.response.ReceivedReviewsResponse;
import com.buddygo.buddygoserver.domain.review.dto.response.ReviewTagsResponse;
import com.buddygo.buddygoserver.domain.review.dto.response.ReviewTargetsResponse;
import com.buddygo.buddygoserver.domain.review.dto.response.ReviewTagsResponse.TagResponse;
import com.buddygo.buddygoserver.domain.review.dto.response.ReviewTargetsResponse.TargetResponse;
import com.buddygo.buddygoserver.domain.review.entity.Review;
import com.buddygo.buddygoserver.domain.review.enums.ReviewRating;
import com.buddygo.buddygoserver.domain.review.enums.ReviewTagType;
import com.buddygo.buddygoserver.domain.review.repository.ReviewCursor;
import com.buddygo.buddygoserver.domain.review.repository.ReviewRepository;
import com.buddygo.buddygoserver.domain.review.repository.ReviewTagRepository;
import com.buddygo.buddygoserver.domain.review.service.ReviewEligibility.Context;
import com.buddygo.buddygoserver.domain.user.exception.UserErrorType;
import com.buddygo.buddygoserver.domain.user.repository.UserRepository;
import com.buddygo.buddygoserver.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewQueryService {

    private static final Set<ReviewRating> ALL_RATINGS = EnumSet.allOf(ReviewRating.class);
    private static final Set<ReviewRating> PUBLIC_RATINGS = EnumSet.complementOf(EnumSet.of(ReviewRating.BAD));

    private final ReviewRepository reviewRepository;
    private final ReviewTagRepository reviewTagRepository;
    private final UserRepository userRepository;
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

    /** 내가 받은 후기. 아쉬움 평가도 보이지만 평가자와 모임은 비운다. 최신순. */
    public ReceivedReviewsResponse getMyReceived(Long userId, String cursor, int size) {
        return received(userId, ALL_RATINGS, cursor, size);
    }

    /** 다른 사람이 받은 후기. 아쉬움 평가는 공개하지 않는다. 최신순. */
    public ReceivedReviewsResponse getReceived(Long targetId, String cursor, int size) {
        if (!userRepository.existsById(targetId)) {
            throw new CustomException(UserErrorType.NOT_FOUND);
        }
        return received(targetId, PUBLIC_RATINGS, cursor, size);
    }

    private ReceivedReviewsResponse received(Long targetId, Set<ReviewRating> ratings, String cursor, int size) {
        ReviewCursor after = ReviewCursor.decode(cursor);
        List<Review> fetched = reviewRepository.findReceived(targetId, ratings, after.createdAt(), after.reviewId(),
                Limit.of(size + 1));
        boolean hasNext = fetched.size() > size;
        List<Review> page = hasNext ? fetched.subList(0, size) : fetched;
        Map<Long, List<TagResponse>> tags = tagsOf(page);
        return new ReceivedReviewsResponse(
                page.stream().map(r -> ReceivedReviewResponse.of(r, tags.getOrDefault(r.getId(), List.of()))).toList(),
                hasNext ? ReviewCursor.after(page.getLast()).encode() : null);
    }

    /** 평가마다 태그(태그 목록 순) */
    private Map<Long, List<TagResponse>> tagsOf(List<Review> reviews) {
        if (reviews.isEmpty()) {
            return Map.of();
        }
        return reviewTagRepository.findByReviewIds(reviews.stream().map(Review::getId).toList()).stream()
                .collect(Collectors.groupingBy(ReviewTagRepository.ReviewTagRow::getReviewId,
                        Collectors.collectingAndThen(Collectors.toList(), rows -> rows.stream()
                                .map(row -> ReviewTagType.valueOf(row.getTag()))
                                .sorted()
                                .map(t -> new TagResponse(t, t.getLabel()))
                                .toList())));
    }
}
