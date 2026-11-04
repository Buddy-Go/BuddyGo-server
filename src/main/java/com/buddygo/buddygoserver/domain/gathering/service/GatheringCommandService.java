package com.buddygo.buddygoserver.domain.gathering.service;

import com.buddygo.buddygoserver.domain.application.entity.Participation;
import com.buddygo.buddygoserver.domain.application.repository.ParticipationRepository;
import com.buddygo.buddygoserver.domain.gathering.config.GatheringProperties;
import com.buddygo.buddygoserver.domain.gathering.dto.request.GatheringCreateRequest;
import com.buddygo.buddygoserver.domain.gathering.dto.request.PlaceRequest;
import com.buddygo.buddygoserver.domain.gathering.dto.response.GatheringDetailResponse;
import com.buddygo.buddygoserver.domain.gathering.entity.Gathering;
import com.buddygo.buddygoserver.domain.gathering.entity.Place;
import com.buddygo.buddygoserver.domain.gathering.enums.GenderRule;
import com.buddygo.buddygoserver.domain.gathering.exception.GatheringErrorType;
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
