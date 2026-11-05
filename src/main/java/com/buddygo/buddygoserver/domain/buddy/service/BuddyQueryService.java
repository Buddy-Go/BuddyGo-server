package com.buddygo.buddygoserver.domain.buddy.service;

import com.buddygo.buddygoserver.domain.application.entity.Participation;
import com.buddygo.buddygoserver.domain.application.repository.ParticipationRepository;
import com.buddygo.buddygoserver.domain.buddy.dto.response.ActivityResponse;
import com.buddygo.buddygoserver.domain.buddy.dto.response.BuddyResponse;
import com.buddygo.buddygoserver.domain.buddy.entity.Buddy;
import com.buddygo.buddygoserver.domain.buddy.exception.BuddyErrorType;
import com.buddygo.buddygoserver.domain.buddy.repository.BuddyRepository;
import com.buddygo.buddygoserver.domain.gathering.config.GatheringProperties;
import com.buddygo.buddygoserver.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BuddyQueryService {

    /** 대표 활동: 지금 운동 중인 것이 먼저, 그다음 시작이 이른 것 */
    private static final Comparator<ActivityResponse> REPRESENTATIVE = Comparator
            .comparing((ActivityResponse a) -> !a.inProgress())
            .thenComparing(ActivityResponse::startAt);

    private final BuddyRepository buddyRepository;
    private final ParticipationRepository participationRepository;
    private final GatheringProperties gatheringProperties;
    private final Clock clock;

    /** 버디 목록: 사람마다 대표 활동 하나. 지금 운동 중 → 예정 모임이 가까운 순 → 예정 없음 순으로 늘어놓는다. */
    public List<BuddyResponse> getBuddies(Long userId) {
        List<Buddy> buddies = buddyRepository.findAllWithBuddy(userId);
        if (buddies.isEmpty()) {
            return List.of();
        }
        Map<Long, List<ActivityResponse>> activities = activitiesOf(
                buddies.stream().map(b -> b.getBuddy().getId()).toList());
        Comparator<BuddyResponse> order = Comparator
                .comparing((BuddyResponse b) -> b.activity(), Comparator.nullsLast(REPRESENTATIVE))
                .thenComparing(BuddyResponse::nickname);
        return buddies.stream()
                .map(b -> BuddyResponse.of(b.getBuddy(),
                        activities.getOrDefault(b.getBuddy().getId(), List.of()).stream().min(REPRESENTATIVE).orElse(null),
                        b.isNotifyGatheringCreated(), b.isNotifyGatheringApplied()))
                .sorted(order)
                .toList();
    }

    /** 버디의 활동 전체(개설·신청 대기·확정, 지금 운동 중). 버디만 볼 수 있다. */
    public List<ActivityResponse> getActivities(Long userId, Long targetId) {
        if (!buddyRepository.areBuddies(userId, targetId)) {
            throw new CustomException(BuddyErrorType.NOT_BUDDY);
        }
        return activitiesOf(List.of(targetId)).getOrDefault(targetId, List.of()).stream()
                .sorted(REPRESENTATIVE)
                .toList();
    }

    private Map<Long, List<ActivityResponse>> activitiesOf(List<Long> userIds) {
        Instant now = Instant.now(clock);
        List<Participation> participations = participationRepository.findActivities(
                userIds, now, now.plus(gatheringProperties.deadlineBeforeStart()));
        return participations.stream().collect(Collectors.groupingBy(
                p -> p.getUser().getId(),
                Collectors.mapping(p -> ActivityResponse.of(p, now), Collectors.toList())));
    }
}
