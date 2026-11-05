package com.buddygo.buddygoserver.domain.buddy.service;

import com.buddygo.buddygoserver.domain.application.entity.Participation;
import com.buddygo.buddygoserver.domain.application.repository.ParticipationRepository;
import com.buddygo.buddygoserver.domain.buddy.dto.response.ActivityResponse;
import com.buddygo.buddygoserver.domain.buddy.dto.response.BuddyResponse;
import com.buddygo.buddygoserver.domain.buddy.dto.response.WorkoutPartnerResponse;
import com.buddygo.buddygoserver.domain.buddy.entity.Buddy;
import com.buddygo.buddygoserver.domain.buddy.entity.BuddyRequest;
import com.buddygo.buddygoserver.domain.buddy.enums.BuddyRelation;
import com.buddygo.buddygoserver.domain.buddy.exception.BuddyErrorType;
import com.buddygo.buddygoserver.domain.buddy.repository.BuddyRepository;
import com.buddygo.buddygoserver.domain.buddy.repository.BuddyRequestRepository;
import com.buddygo.buddygoserver.domain.checkin.repository.CheckInRepository;
import com.buddygo.buddygoserver.domain.gathering.config.GatheringProperties;
import com.buddygo.buddygoserver.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private final BuddyRequestRepository buddyRequestRepository;
    private final ParticipationRepository participationRepository;
    private final CheckInRepository checkInRepository;
    private final GatheringProperties gatheringProperties;
    private final Clock clock;

    /** 나와 상대의 관계와, 대기 중인 신청이 있으면 그 ID */
    public record RelationState(BuddyRelation relation, Long requestId) {

        static final RelationState NONE = new RelationState(BuddyRelation.NONE, null);
    }

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

    /** 함께 운동한 사람(같은 모임에서 둘 다 체크인). 최근에 함께한 순이고, 닉네임 일부로 찾을 수 있다. */
    public List<WorkoutPartnerResponse> getWorkoutPartners(Long userId, String query) {
        List<CheckInRepository.WorkoutPartner> partners =
                checkInRepository.findWorkoutPartners(userId, nicknamePattern(query));
        if (partners.isEmpty()) {
            return List.of();
        }
        Map<Long, RelationState> relations = relationsWith(userId,
                partners.stream().map(CheckInRepository.WorkoutPartner::getUserId).toList());
        return partners.stream()
                .map(p -> {
                    RelationState state = relations.get(p.getUserId());
                    return new WorkoutPartnerResponse(p.getUserId(), p.getNickname(), p.getWorkoutCount(),
                            p.getLastWorkoutAt(), state.relation(), state.requestId());
                })
                .toList();
    }

    /** 상대들과 나의 버디 관계. 프로필 조회의 버디 버튼도 이 값을 쓴다. */
    public Map<Long, RelationState> relationsWith(Long userId, Collection<Long> otherIds) {
        Set<Long> buddyIds = new HashSet<>(buddyRepository.findBuddyIdsAmong(userId, otherIds));
        Map<Long, RelationState> pending = new HashMap<>();
        for (BuddyRequest request : buddyRequestRepository.findPendingBetween(userId, otherIds)) {
            boolean sentByMe = request.isSentBy(userId);
            Long otherId = sentByMe ? request.getReceiver().getId() : request.getSender().getId();
            pending.put(otherId, new RelationState(
                    sentByMe ? BuddyRelation.REQUEST_SENT : BuddyRelation.REQUEST_RECEIVED, request.getId()));
        }
        return otherIds.stream().distinct().collect(Collectors.toMap(id -> id, id -> buddyIds.contains(id)
                ? new RelationState(BuddyRelation.BUDDY, null)
                : pending.getOrDefault(id, RelationState.NONE)));
    }

    private Map<Long, List<ActivityResponse>> activitiesOf(List<Long> userIds) {
        Instant now = Instant.now(clock);
        List<Participation> participations = participationRepository.findActivities(
                userIds, now, now.plus(gatheringProperties.deadlineBeforeStart()));
        return participations.stream().collect(Collectors.groupingBy(
                p -> p.getUser().getId(),
                Collectors.mapping(p -> ActivityResponse.of(p, now), Collectors.toList())));
    }

    /** 닉네임 일부 검색용 ILIKE 패턴. LIKE 특수문자는 글자 그대로 찾는다. */
    private static String nicknamePattern(String query) {
        if (query == null || query.isBlank()) {
            return "%";
        }
        String escaped = query.strip().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
