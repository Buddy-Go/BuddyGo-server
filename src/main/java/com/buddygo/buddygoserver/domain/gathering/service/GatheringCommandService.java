package com.buddygo.buddygoserver.domain.gathering.service;

import com.buddygo.buddygoserver.domain.application.entity.Participation;
import com.buddygo.buddygoserver.domain.application.enums.RejectReason;
import com.buddygo.buddygoserver.domain.application.repository.ParticipationRepository;
import com.buddygo.buddygoserver.domain.gathering.config.GatheringProperties;
import com.buddygo.buddygoserver.domain.gathering.dto.request.GatheringCreateRequest;
import com.buddygo.buddygoserver.domain.gathering.dto.request.GatheringUpdateRequest;
import com.buddygo.buddygoserver.domain.gathering.dto.request.PlaceRequest;
import com.buddygo.buddygoserver.domain.gathering.dto.response.GatheringDetailResponse;
import com.buddygo.buddygoserver.domain.gathering.entity.Gathering;
import com.buddygo.buddygoserver.domain.gathering.entity.GatheringCancellation;
import com.buddygo.buddygoserver.domain.gathering.entity.Place;
import com.buddygo.buddygoserver.domain.gathering.enums.GenderRule;
import com.buddygo.buddygoserver.domain.gathering.exception.GatheringErrorType;
import com.buddygo.buddygoserver.domain.gathering.repository.GatheringCancellationRepository;
import com.buddygo.buddygoserver.domain.gathering.repository.GatheringRepository;
import com.buddygo.buddygoserver.domain.gathering.repository.PlaceRepository;
import com.buddygo.buddygoserver.domain.sport.gym.service.GatheringGymService;
import com.buddygo.buddygoserver.domain.user.entity.User;
import com.buddygo.buddygoserver.domain.user.exception.UserErrorType;
import com.buddygo.buddygoserver.domain.user.repository.UserRepository;
import com.buddygo.buddygoserver.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
@Transactional
public class GatheringCommandService {

    private static final GeometryFactory WGS84 = new GeometryFactory(new PrecisionModel(), 4326);

    private final GatheringRepository gatheringRepository;
    private final PlaceRepository placeRepository;
    private final GatheringCancellationRepository gatheringCancellationRepository;
    private final ParticipationRepository participationRepository;
    private final UserRepository userRepository;
    private final GatheringGymService gatheringGymService;
    private final GatheringQueryService gatheringQueryService;
    private final GatheringProperties gatheringProperties;
    private final Clock clock;

    /** 개설. 호스트도 확정 참여 레코드를 가진다. 같은 시간대에 다른 모임이 있어도 막지 않는다. */
    public GatheringDetailResponse create(Long hostId, GatheringCreateRequest request) {
        Instant now = Instant.now(clock);
        User host = userRepository.findById(hostId)
                .orElseThrow(() -> new CustomException(UserErrorType.NOT_FOUND));
        validateStartAt(request.startAt(), now);
        Duration duration = toDuration(request.durationMinutes());
        short capacity = toCapacity(request.capacity());
        validateGenderRule(request.genderRule(), host);

        Gathering gathering = gatheringRepository.save(Gathering.open(host, findOrCreatePlace(request.place(), now),
                request.title().strip(), blankToNull(request.description()), blankToNull(request.detailAddress()),
                request.startAt(), duration, capacity, request.genderRule()));
        participationRepository.save(Participation.hostOf(gathering, now));
        gatheringGymService.saveCriteria(gathering, request.gym().parts(), request.gym().showMyRecord());

        return gatheringQueryService.getDetail(gathering.getId(), hostId);
    }

    /**
     * 수정. 확정 참가자(호스트 제외)가 0명이면 모든 항목, 1명 이상이면 제목·설명·상세주소만 바꿀 수 있다.
     * 잠긴 항목을 보내면 무시하지 않고 거절한다.
     */
    public GatheringDetailResponse update(Long userId, Long gatheringId, GatheringUpdateRequest request) {
        Instant now = Instant.now(clock);
        Gathering gathering = lockForHostAction(gatheringId, userId, now);
        if (request.changesCore() && participationRepository.countConfirmedExcludingHost(gatheringId) > 0) {
            throw new CustomException(GatheringErrorType.EDIT_RESTRICTED);
        }

        gathering.changeDetails(
                request.title() != null ? request.title().strip() : gathering.getTitle(),
                request.description() != null ? blankToNull(request.description()) : gathering.getDescription(),
                request.detailAddress() != null ? blankToNull(request.detailAddress()) : gathering.getDetailAddress());

        if (request.changesCore()) {
            if (request.startAt() != null) {
                validateStartAt(request.startAt(), now);
            }
            GenderRule genderRule = request.genderRule() != null ? request.genderRule() : gathering.getGenderRule();
            validateGenderRule(genderRule, gathering.getHost());
            gathering.changeCore(
                    request.place() != null ? findOrCreatePlace(request.place(), now) : gathering.getPlace(),
                    request.startAt() != null ? request.startAt() : gathering.getStartAt(),
                    request.durationMinutes() != null ? toDuration(request.durationMinutes()) : gathering.getDuration(),
                    request.capacity() != null ? toCapacity(request.capacity()) : gathering.getCapacity(),
                    genderRule);
            if (request.gym() != null) {
                gatheringGymService.replaceCriteria(gathering, request.gym().parts(), request.gym().showMyRecord());
            }
        }
        // TODO 확정 참가자가 없을 때 성별 조건을 바꾸면 조건에 맞지 않게 된 대기 신청 처리 (규칙 미정)

        return gatheringQueryService.getDetail(gatheringId, userId);
    }

    /** 삭제. 확정 참가자(호스트 제외)가 0명일 때만. 대기 신청은 자동 거절되고 매너온도 영향은 없다. */
    public void delete(Long userId, Long gatheringId) {
        Instant now = Instant.now(clock);
        Gathering gathering = lockForHostAction(gatheringId, userId, now);
        if (participationRepository.countConfirmedExcludingHost(gatheringId) > 0) {
            throw new CustomException(GatheringErrorType.HAS_CONFIRMED_PARTICIPANTS);
        }
        participationRepository.rejectAllPending(gatheringId, RejectReason.MEETING_DELETED, now);
        gathering.delete(now);
        // TODO 자동 거절 알림 (알림 도메인 구현 후)
    }

    /** 호스트 취소(방폭). 확정 참가자(호스트 제외)가 1명 이상일 때만. 대기 신청은 자동 거절된다. */
    public void cancel(Long userId, Long gatheringId, String reason) {
        Instant now = Instant.now(clock);
        Gathering gathering = lockForHostAction(gatheringId, userId, now);
        if (participationRepository.countConfirmedExcludingHost(gatheringId) == 0) {
            throw new CustomException(GatheringErrorType.NO_CONFIRMED_PARTICIPANTS);
        }
        participationRepository.rejectAllPending(gatheringId, RejectReason.MEETING_CANCELED, now);
        gathering.cancel();
        gatheringCancellationRepository.save(GatheringCancellation.byHost(gathering, reason.strip(), now));
        // TODO 호스트 매너온도 하락(마감 후 취소가 더 크게, 점수 미정) · 확정 참가자에게 사유 알림 · 자동 거절 알림
    }

    /**
     * 호스트만, 취소되지 않았고 아직 시작 전인 모임에 할 수 있는 수정·삭제·취소의 공통 검사.
     * 데드라인이 지난 모집중 모임은 스케줄러가 아직 돌지 않았어도 정원 미달 자동 취소 대상이라 막는다.
     * 그렇지 않으면 시작을 미뤄 자동 취소를 피하거나, 페널티 없는 인원 미달 취소가 호스트 취소로 남는다.
     */
    private Gathering lockForHostAction(Long gatheringId, Long userId, Instant now) {
        Gathering gathering = gatheringRepository.findActiveByIdForUpdate(gatheringId)
                .orElseThrow(() -> new CustomException(GatheringErrorType.NOT_FOUND));
        if (!gathering.isHostedBy(userId)) {
            throw new CustomException(GatheringErrorType.NOT_HOST);
        }
        if (gathering.isCanceled()) {
            throw new CustomException(GatheringErrorType.ALREADY_CANCELED);
        }
        if (gathering.hasStarted(now)) {
            throw new CustomException(GatheringErrorType.ALREADY_STARTED);
        }
        if (gathering.isUnfilledAtDeadline(now, gatheringProperties.deadlineOf(gathering.getStartAt()))) {
            throw new CustomException(GatheringErrorType.DEADLINE_PASSED_UNFILLED);
        }
        return gathering;
    }

    /** 시작 시각은 단위(start-time-step)의 정각이고, 데드라인(시작 - deadline-before-start)이 미래여야 한다. */
    private void validateStartAt(Instant startAt, Instant now) {
        if (!gatheringProperties.isOnStartTimeStep(startAt)) {
            throw new CustomException(GatheringErrorType.INVALID_START_TIME,
                    "시작 시각은 " + gatheringProperties.startTimeStep().toMinutes() + "분 단위로 정해야 합니다.");
        }
        if (!gatheringProperties.deadlineOf(startAt).isAfter(now)) {
            throw new CustomException(GatheringErrorType.START_TOO_SOON,
                    "시작 시각은 지금부터 " + gatheringProperties.deadlineBeforeStart().toMinutes() + "분 이후여야 합니다.");
        }
    }

    private Duration toDuration(int minutes) {
        Duration duration = Duration.ofMinutes(minutes);
        if (!gatheringProperties.isValidDuration(duration)) {
            throw new CustomException(GatheringErrorType.INVALID_DURATION,
                    "진행 시간은 " + gatheringProperties.minDuration().toMinutes() + "~"
                            + gatheringProperties.maxDuration().toMinutes() + "분, "
                            + gatheringProperties.durationStep().toMinutes() + "분 단위입니다.");
        }
        return duration;
    }

    private short toCapacity(int capacity) {
        if (!gatheringProperties.isValidCapacity(capacity)) {
            throw new CustomException(GatheringErrorType.INVALID_CAPACITY,
                    "정원은 호스트 포함 " + gatheringProperties.minCapacity() + "~"
                            + gatheringProperties.maxCapacity() + "명입니다.");
        }
        return (short) capacity;
    }

    private static void validateGenderRule(GenderRule genderRule, User host) {
        if (!genderRule.allows(host.getGender())) {
            throw new CustomException(GatheringErrorType.GENDER_RULE_NOT_ALLOWED);
        }
    }

    /** 카카오 장소는 kakao_place_id 로 찾거나 만들고, 지도에서 찍은 집결지는 새로 만든다. */
    private Place findOrCreatePlace(PlaceRequest request, Instant now) {
        String kakaoPlaceId = blankToNull(request.kakaoPlaceId());
        if (kakaoPlaceId == null) {
            return placeRepository.save(Place.pinned(request.name(), blankToNull(request.roadAddress()),
                    request.jibunAddress(), request.regionCode(),
                    WGS84.createPoint(new Coordinate(request.longitude(), request.latitude()))));
        }
        Long placeId = placeRepository.upsertKakaoPlace(kakaoPlaceId, request.name(),
                blankToNull(request.roadAddress()), request.jibunAddress(), request.regionCode(),
                request.latitude(), request.longitude(), now);
        return placeRepository.findById(placeId)
                .orElseThrow(() -> new IllegalStateException("방금 저장한 장소가 없습니다: " + placeId));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
