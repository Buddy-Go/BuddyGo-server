package com.buddygo.buddygoserver.domain.review.service;

import com.buddygo.buddygoserver.domain.gathering.exception.GatheringErrorType;
import com.buddygo.buddygoserver.domain.gathering.repository.GatheringRepository;
import com.buddygo.buddygoserver.domain.review.config.ReviewProperties;
import com.buddygo.buddygoserver.domain.review.dto.request.ReviewSubmitRequest;
import com.buddygo.buddygoserver.domain.review.dto.response.ReviewResponse;
import com.buddygo.buddygoserver.domain.manner.service.MannerTemperatureService;
import com.buddygo.buddygoserver.domain.review.entity.Review;
import com.buddygo.buddygoserver.domain.review.entity.ReviewTag;
import com.buddygo.buddygoserver.domain.review.enums.ReviewTagType;
import com.buddygo.buddygoserver.domain.review.enums.ReviewTagType.Polarity;
import com.buddygo.buddygoserver.domain.review.exception.ReviewErrorType;
import com.buddygo.buddygoserver.domain.review.repository.ReviewRepository;
import com.buddygo.buddygoserver.domain.review.repository.ReviewTagRepository;
import com.buddygo.buddygoserver.domain.review.service.ReviewEligibility.Context;
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
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional
public class ReviewCommandService {

    private final GatheringRepository gatheringRepository;
    private final UserRepository userRepository;
    private final ReviewRepository reviewRepository;
    private final ReviewTagRepository reviewTagRepository;
    private final ReviewEligibility eligibility;
    private final MannerTemperatureService mannerTemperatureService;
    private final ReviewProperties reviewProperties;
    private final Clock clock;

    /**
     * 한 사람에 대한 평가를 제출하고 곧바로 상대 매너온도에 반영한다. 제출한 평가는 고칠 수 없다.
     * 잠금 순서는 모임(FOR KEY SHARE) → 상대 사용자 행이다. 서로를 동시에 평가해도 잠그는 사용자 행이 하나씩이라
     * 교착이 생기지 않는다.
     */
    public ReviewResponse submit(Long userId, Long gatheringId, ReviewSubmitRequest request) {
        // 평가 행이 모임을 참조하므로 동시에 모임이 지워지지 않게 잡는다(모임 삭제와 겹치면 FK 위반이 500 이 된다)
        if (!gatheringRepository.lockForKeyShare(gatheringId)) {
            throw new CustomException(GatheringErrorType.NOT_FOUND);
        }
        Instant now = Instant.now(clock);
        Context context = eligibility.require(userId, gatheringId, now);
        if (!context.isOpen(now)) {
            throw new CustomException(ReviewErrorType.PERIOD_EXPIRED);
        }
        Long targetId = request.targetId();
        // 자기 자신은 ck_reviews_not_self(CHECK)라 DB 까지 가면 500 이 된다
        if (targetId.equals(userId) || !context.isAttendee(targetId)) {
            throw new CustomException(ReviewErrorType.INVALID_TARGET);
        }
        if (reviewRepository.existsByGatheringIdAndEvaluatorIdAndTargetId(gatheringId, userId, targetId)) {
            throw new CustomException(ReviewErrorType.ALREADY_SUBMITTED);
        }

        // 온도를 읽고 더하므로 상대 행을 잠근다. 상대 User 를 다른 경로로 먼저 읽지 않아야 잠근 시점의 값을 본다.
        User target = userRepository.findByIdForUpdate(targetId)
                .orElseThrow(() -> new CustomException(UserErrorType.NOT_FOUND));
        String comment = request.comment() == null || request.comment().isBlank() ? null : request.comment().strip();
        Review review = reviewRepository.saveAndFlush(Review.of(context.gathering(),
                userRepository.getReferenceById(userId), target, request.rating(), comment));

        Set<ReviewTagType> tags = request.tags();
        List<ReviewTagType> sortedTags = tags.stream().sorted().toList();
        reviewTagRepository.saveAll(sortedTags.stream().map(tag -> ReviewTag.of(review, tag)).toList());

        long positive = tags.stream().filter(t -> t.getPolarity() == Polarity.POSITIVE).count();
        mannerTemperatureService.applyReview(target, review.getId(),
                reviewProperties.score().of(request.rating(), positive, tags.size() - positive));
        return ReviewResponse.of(review, sortedTags);
    }
}
