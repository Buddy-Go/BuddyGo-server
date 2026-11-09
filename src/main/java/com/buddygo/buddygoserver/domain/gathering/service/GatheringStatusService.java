package com.buddygo.buddygoserver.domain.gathering.service;

import com.buddygo.buddygoserver.domain.application.enums.RejectReason;
import com.buddygo.buddygoserver.domain.application.repository.ParticipationRepository;
import com.buddygo.buddygoserver.domain.checkin.config.CheckInProperties;
import com.buddygo.buddygoserver.domain.checkin.repository.CheckInRepository;
import com.buddygo.buddygoserver.domain.gathering.config.GatheringProperties;
import com.buddygo.buddygoserver.domain.gathering.entity.Gathering;
import com.buddygo.buddygoserver.domain.gathering.entity.GatheringCancellation;
import com.buddygo.buddygoserver.domain.gathering.enums.GatheringStatus;
import com.buddygo.buddygoserver.domain.gathering.repository.GatheringCancellationRepository;
import com.buddygo.buddygoserver.domain.gathering.repository.GatheringRepository;
import com.buddygo.buddygoserver.domain.manner.service.MannerTemperatureService;
import com.buddygo.buddygoserver.domain.user.entity.User;
import com.buddygo.buddygoserver.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 시간에 따른 모임 상태 전이 (서비스 규칙 정의서 5장 · 6장 · 7장). {@code GatheringStatusScheduler} 가 후보 ID 를 받아
 * 모임마다 따로 호출하므로 한 모임의 실패가 다른 모임을 막지 않는다.
 *
 * <p>후보 조회와 처리 사이에 나가기·수락 등이 끼어들 수 있어, 처리할 때는 모임 행을 잠그고 조건을 다시 확인한다.
 * 이미 처리됐거나 조건이 바뀐 모임은 건너뛰므로 여러 번 돌려도 결과가 같다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class GatheringStatusService {

    private final GatheringRepository gatheringRepository;
    private final GatheringCancellationRepository gatheringCancellationRepository;
    private final ParticipationRepository participationRepository;
    private final CheckInRepository checkInRepository;
    private final UserRepository userRepository;
    private final MannerTemperatureService mannerTemperatureService;
    private final GatheringProperties gatheringProperties;
    private final CheckInProperties checkInProperties;

    /** 데드라인이 지났는데 아직 모집중(정원 미달)인 모임 */
    @Transactional(readOnly = true)
    public List<Long> findUnderCapacityAtDeadline(Instant now) {
        return gatheringRepository.findIdsByStatusStartingBy(
                GatheringStatus.RECRUITING, now.plus(gatheringProperties.deadlineBeforeStart()));
    }

    /** 시작 시각이 된 마감 모임 */
    @Transactional(readOnly = true)
    public List<Long> findStartable(Instant now) {
        return gatheringRepository.findIdsByStatusStartingBy(GatheringStatus.CLOSED, now);
    }

    /** 노쇼 판정 시각(시작 + no-show-after-start)이 지난 진행중 모임 */
    @Transactional(readOnly = true)
    public List<Long> findNoShowDue(Instant now) {
        return gatheringRepository.findIdsByStatusStartingBy(
                GatheringStatus.IN_PROGRESS, now.minus(checkInProperties.noShowAfterStart()));
    }

    /** 종료 시각이 된 진행중 모임 */
    @Transactional(readOnly = true)
    public List<Long> findFinishable(Instant now) {
        return gatheringRepository.findIdsByStatusEndingBy(GatheringStatus.IN_PROGRESS, now);
    }

    /**
     * 데드라인에 모집중이면(정원 미달) 자동 취소하고 대기 신청을 자동 거절한다. 누구의 매너온도에도 영향이 없다.
     * 데드라인에 마감 상태인 모임은 그대로 확정되므로 할 일이 없다.
     */
    public void cancelUnderCapacity(Long gatheringId, Instant now) {
        Gathering gathering = lock(gatheringId);
        if (gathering == null
                || !gathering.isUnfilledAtDeadline(now, gatheringProperties.deadlineOf(gathering.getStartAt()))) {
            return;
        }
        participationRepository.rejectAllPending(gatheringId, RejectReason.MEETING_CANCELED, now);
        gathering.cancel();
        gatheringCancellationRepository.save(GatheringCancellation.underCapacity(gathering, now));
        // TODO 인원 미달 취소 알림(호스트 + 확정 참가자) · 자동 거절 알림 (알림 도메인 구현 후)
    }

    public void start(Long gatheringId, Instant now) {
        Gathering gathering = lock(gatheringId);
        if (gathering == null || gathering.getStatus() != GatheringStatus.CLOSED
                || now.isBefore(gathering.getStartAt())) {
            return;
        }
        gathering.start();
        // TODO 운동 시작 알림(아직 체크인하지 않은 호스트·참가자)
    }

    /** 노쇼 판정 시각까지 체크인하지 않은 확정 참가자(호스트 포함)를 노쇼로 남긴다. 호스트가 노쇼여도 모임은 진행된다. */
    public void markNoShows(Long gatheringId, Instant now) {
        Gathering gathering = lock(gatheringId);
        if (gathering == null || gathering.getStatus() != GatheringStatus.IN_PROGRESS
                || now.isBefore(checkInProperties.noShowAt(gathering.getStartAt()))) {
            return;
        }
        insertNoShows(gathering);
    }

    /**
     * 종료 시각이 되면 종료한다. 노쇼 처리가 빠졌더라도 종료 전에 한 번 더 남긴다(이미 남은 기록은 건너뛴다).
     * 호스트가 체크인(정상·지각)했으면 호스트 출석 보너스를 준다(규칙 11장). 취소된 모임은 여기 오지 않는다.
     */
    public void finish(Long gatheringId, Instant now) {
        Gathering gathering = lock(gatheringId);
        if (gathering == null || gathering.getStatus() != GatheringStatus.IN_PROGRESS
                || now.isBefore(gathering.getEndAt())) {
            return;
        }
        List<Long> noShowUserIds = checkInRepository.insertNoShows(gathering.getId());
        Long hostId = checkInRepository.hostAttended(gathering.getId()) ? gathering.getHost().getId() : null;
        applyAttendanceResults(gathering, noShowUserIds, hostId);
        gathering.finish();
        // TODO 모임 종료 · 평가 가능 알림(체크인한 사람)
    }

    /**
     * 노쇼를 기록하고 새로 노쇼가 된 사람에게만 페널티를 준다. 모임 행을 잡은 상태에서 사용자 행을 id 오름차순으로 잠근다
     * (모임 → 사용자 순서는 평가·지각 체크인과 같다).
     */
    private void insertNoShows(Gathering gathering) {
        applyAttendanceResults(gathering, checkInRepository.insertNoShows(gathering.getId()), null);
    }

    /**
     * 새로 노쇼가 된 사람에게 페널티를, 출석한 호스트(attendedHostId, 없으면 null)에게 보너스를 준다.
     * 사용자 행은 한 번에 id 오름차순으로 잠근다. 따로 잠그면 호스트가 노쇼한 사람보다 id 가 작을 때 순서가 어긋난다.
     * 호스트는 출석했으면 노쇼가 아니므로 두 집합은 겹치지 않는다.
     */
    private void applyAttendanceResults(Gathering gathering, List<Long> noShowUserIds, Long attendedHostId) {
        Set<Long> userIds = new HashSet<>(noShowUserIds);
        if (attendedHostId != null) {
            userIds.add(attendedHostId);
        }
        if (userIds.isEmpty()) {
            return;
        }
        Set<Long> noShows = Set.copyOf(noShowUserIds);
        for (User user : userRepository.findAllByIdsForUpdate(userIds)) {
            if (noShows.contains(user.getId())) {
                mannerTemperatureService.applyNoShow(user, gathering.getId());
            } else {
                mannerTemperatureService.applyHostAttendance(user, gathering.getId());
            }
        }
    }

    /** 후보 조회 뒤 삭제된 모임은 null */
    private Gathering lock(Long gatheringId) {
        return gatheringRepository.findByIdForUpdate(gatheringId).orElse(null);
    }
}
