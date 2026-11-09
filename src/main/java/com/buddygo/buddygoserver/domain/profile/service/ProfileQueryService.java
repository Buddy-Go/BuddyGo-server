package com.buddygo.buddygoserver.domain.profile.service;

import com.buddygo.buddygoserver.domain.buddy.enums.BuddyRelation;
import com.buddygo.buddygoserver.domain.buddy.service.BuddyQueryService;
import com.buddygo.buddygoserver.domain.buddy.service.BuddyQueryService.RelationState;
import com.buddygo.buddygoserver.domain.checkin.repository.CheckInRepository;
import com.buddygo.buddygoserver.domain.gathering.enums.GatheringStatus;
import com.buddygo.buddygoserver.domain.gathering.repository.GatheringRepository;
import com.buddygo.buddygoserver.domain.profile.dto.response.MyProfileResponse;
import com.buddygo.buddygoserver.domain.profile.dto.response.ProfileStatsResponse;
import com.buddygo.buddygoserver.domain.profile.dto.response.UserProfileResponse;
import com.buddygo.buddygoserver.domain.profile.dto.response.UserProfileResponse.BuddyStatusResponse;
import com.buddygo.buddygoserver.domain.review.dto.response.ReviewSummaryResponse;
import com.buddygo.buddygoserver.domain.review.service.ReviewQueryService;
import com.buddygo.buddygoserver.domain.user.entity.User;
import com.buddygo.buddygoserver.domain.user.exception.UserErrorType;
import com.buddygo.buddygoserver.domain.user.repository.UserRepository;
import com.buddygo.buddygoserver.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * 마이페이지·다른 사용자 프로필. 후기·체크인·모임·버디를 읽어 모으기만 하는 곳이라 다른 도메인은 profile 을 참조하지 않는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProfileQueryService {

    /** 실제로 진행된 모임: 개설 횟수에 센다 */
    private static final Set<GatheringStatus> HELD = EnumSet.of(GatheringStatus.IN_PROGRESS, GatheringStatus.FINISHED);

    private final UserRepository userRepository;
    private final GatheringRepository gatheringRepository;
    private final CheckInRepository checkInRepository;
    private final ReviewQueryService reviewQueryService;
    private final BuddyQueryService buddyQueryService;

    public MyProfileResponse getMe(Long userId) {
        User user = find(userId);
        return MyProfileResponse.of(user, statsOf(userId));
    }

    public UserProfileResponse getUser(Long viewerId, Long userId) {
        User user = find(userId);
        boolean me = viewerId.equals(userId);
        return UserProfileResponse.of(user, me, statsOf(userId),
                me ? new BuddyStatusResponse(BuddyRelation.NONE, null, false) : buddyStatus(viewerId, userId));
    }

    private ProfileStatsResponse statsOf(Long userId) {
        ReviewSummaryResponse reviews = reviewQueryService.getSummary(userId);
        long participated = checkInRepository.countAttendedByUserIds(List.of(userId)).stream()
                .mapToLong(CheckInRepository.AttendedCount::getCount)
                .sum();
        return new ProfileStatsResponse(reviews.reviewCount(),
                gatheringRepository.countByHostIdAndStatusIn(userId, HELD), participated, reviews.topTags());
    }

    /** 버디 신청 버튼은 아무 관계가 없고 함께 운동한 적이 있을 때만 (규칙 정의서 10장) */
    private BuddyStatusResponse buddyStatus(Long viewerId, Long userId) {
        RelationState state = buddyQueryService.relationsWith(viewerId, List.of(userId)).get(userId);
        boolean canRequest = state.relation() == BuddyRelation.NONE && checkInRepository.workedOutTogether(viewerId, userId);
        return new BuddyStatusResponse(state.relation(), state.requestId(), canRequest);
    }

    private User find(Long userId) {
        return userRepository.findById(userId).orElseThrow(() -> new CustomException(UserErrorType.NOT_FOUND));
    }
}
