package com.buddygo.buddygoserver.domain.checkin.service;

import com.buddygo.buddygoserver.domain.application.entity.Participation;
import com.buddygo.buddygoserver.domain.application.repository.ParticipationRepository;
import com.buddygo.buddygoserver.domain.checkin.config.CheckInProperties;
import com.buddygo.buddygoserver.domain.checkin.dto.request.CheckInRequest;
import com.buddygo.buddygoserver.domain.checkin.dto.response.CheckInResponse;
import com.buddygo.buddygoserver.domain.checkin.entity.CheckIn;
import com.buddygo.buddygoserver.domain.checkin.enums.Attendance;
import com.buddygo.buddygoserver.domain.checkin.exception.CheckInErrorType;
import com.buddygo.buddygoserver.domain.checkin.exception.CheckInOutOfRangeException;
import com.buddygo.buddygoserver.domain.checkin.repository.CheckInRepository;
import com.buddygo.buddygoserver.domain.gathering.entity.Gathering;
import com.buddygo.buddygoserver.domain.gathering.enums.GatheringStatus;
import com.buddygo.buddygoserver.domain.gathering.exception.GatheringErrorType;
import com.buddygo.buddygoserver.domain.gathering.repository.GatheringRepository;
import com.buddygo.buddygoserver.domain.gathering.repository.PlaceRepository;
import com.buddygo.buddygoserver.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * GPS 체크인 (서비스 규칙 정의서 7장). 호스트를 포함한 확정 참가자가 체크인 창 [시작 - open-before-start,
 * 시작 + no-show-after-start) 안에, 모임 장소 radius-meters 안에서 한 번만 한다.
 *
 * <p>모임 행을 먼저 잠가 나가기(데드라인 후 방폭)·호스트 취소·노쇼 기록과 직렬화한다. 그래서 취소와 겹친 체크인은
 * 취소된 상태를 보고 거절되고, 창 마감 경계에서 노쇼 기록과 겹치면 늦은 쪽이 기존 행을 본다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class CheckInCommandService {

    private final GatheringRepository gatheringRepository;
    private final ParticipationRepository participationRepository;
    private final CheckInRepository checkInRepository;
    private final PlaceRepository placeRepository;
    private final CheckInProperties properties;
    private final Clock clock;

    public CheckInResponse checkIn(Long userId, Long gatheringId, CheckInRequest request) {
        Gathering gathering = gatheringRepository.findByIdForUpdate(gatheringId)
                .orElseThrow(() -> new CustomException(GatheringErrorType.NOT_FOUND));
        Participation participation = participationRepository.findActive(gatheringId, userId)
                .filter(Participation::isConfirmed)
                .orElseThrow(() -> new CustomException(CheckInErrorType.NOT_PARTICIPANT));
        // 노쇼 기록은 체크인 창이 닫힌 뒤에만 생기지만, 체크인한 적은 없으므로 사유를 따로 준다
        checkInRepository.findById(participation.getId()).ifPresent(existing -> {
            throw existing.getAttendance() == Attendance.NO_SHOW
                    ? new CustomException(CheckInErrorType.ALREADY_CHECKED_IN, "노쇼로 기록되어 체크인할 수 없습니다.")
                    : new CustomException(CheckInErrorType.ALREADY_CHECKED_IN);
        });

        Instant now = Instant.now(clock);
        requireOpen(gathering, now);

        int distance = placeRepository.distanceMeters(gathering.getPlace().getId(),
                request.latitude(), request.longitude());
        if (distance > properties.radiusMeters()) {
            throw new CheckInOutOfRangeException(distance, properties.radiusMeters());
        }

        Attendance attendance = properties.attendanceAt(gathering.getStartAt(), now);
        // 사전 검사를 지나 동시에 들어온 같은 사람의 두 번째 요청은 check_ins_pkey 위반 → C002
        CheckIn checkIn = checkInRepository.saveAndFlush(CheckIn.attended(participation, attendance, now, distance));
        // TODO 지각 매너온도 하락(점수 미정) — 시스템 페널티(노쇼·나가기·호스트 취소)와 함께 구현
        return CheckInResponse.from(checkIn);
    }

    /**
     * 마감(정원이 찬) 모임만 체크인한다. 시작 시각이 지나도 스케줄러가 진행중으로 바꾸기 전일 수 있어
     * 마감·진행중을 모두 받고, 창은 상태가 아니라 시작 시각과 시계로 판단한다.
     */
    private void requireOpen(Gathering gathering, Instant now) {
        GatheringStatus status = gathering.getStatus();
        if (status != GatheringStatus.CLOSED && status != GatheringStatus.IN_PROGRESS) {
            throw new CustomException(CheckInErrorType.NOT_AVAILABLE_TIME, switch (status) {
                case CANCELED -> "취소된 모임은 체크인할 수 없습니다.";
                case RECRUITING -> "정원이 차지 않은 모임은 체크인할 수 없습니다.";
                default -> "체크인 시간이 지났습니다.";
            });
        }
        if (now.isBefore(properties.openAt(gathering.getStartAt()))) {
            throw new CustomException(CheckInErrorType.NOT_AVAILABLE_TIME, "아직 체크인 시간이 아닙니다.");
        }
        if (!now.isBefore(properties.noShowAt(gathering.getStartAt()))) {
            throw new CustomException(CheckInErrorType.NOT_AVAILABLE_TIME, "체크인 시간이 지났습니다.");
        }
    }
}
