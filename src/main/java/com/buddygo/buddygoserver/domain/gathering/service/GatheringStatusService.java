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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

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
    private final GatheringProperties gatheringProperties;
    private final CheckInProperties checkInProperties;

    /** 데드라인이 지났는데 아직 모집중(정원 미달)인 모임 */
    @Transactional(readOnly = true)
    public List<Long> findUnderCapacityAtDeadline(Instant now) {
        return gatheringRepository.findActiveIdsByStatusStartingBy(
                GatheringStatus.RECRUITING, now.plus(gatheringProperties.deadlineBeforeStart()));
    }

    /** 시작 시각이 된 마감 모임 */
    @Transactional(readOnly = true)
    public List<Long> findStartable(Instant now) {
        return gatheringRepository.findActiveIdsByStatusStartingBy(GatheringStatus.CLOSED, now);
    }

    /** 노쇼 판정 시각(시작 + no-show-after-start)이 지난 진행중 모임 */
    @Transactional(readOnly = true)
    public List<Long> findNoShowDue(Instant now) {
        return gatheringRepository.findActiveIdsByStatusStartingBy(
                GatheringStatus.IN_PROGRESS, now.minus(checkInProperties.noShowAfterStart()));
    }

    /** 종료 시각이 된 진행중 모임 */
    @Transactional(readOnly = true)
    public List<Long> findFinishable(Instant now) {
        return gatheringRepository.findActiveIdsByStatusEndingBy(GatheringStatus.IN_PROGRESS, now);
    }

    /**
     * 데드라인에 모집중이면(정원 미달) 자동 취소하고 대기 신청을 자동 거절한다. 누구의 매너온도에도 영향이 없다.
     * 데드라인에 마감 상태인 모임은 그대로 확정되므로 할 일이 없다.
     */
    public void cancelUnderCapacity(Long gatheringId, Instant now) {
        Gathering gathering = lock(gatheringId);
        if (gathering == null || gathering.getStatus() != GatheringStatus.RECRUITING
                || now.isBefore(gatheringProperties.deadlineOf(gathering.getStartAt()))) {
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

    /** 종료 시각이 되면 종료한다. 노쇼 처리가 빠졌더라도 종료 전에 한 번 더 남긴다(이미 남은 기록은 건너뛴다). */
    public void finish(Long gatheringId, Instant now) {
        Gathering gathering = lock(gatheringId);
        if (gathering == null || gathering.getStatus() != GatheringStatus.IN_PROGRESS
                || now.isBefore(gathering.getEndAt())) {
            return;
        }
        insertNoShows(gathering);
        gathering.finish();
        // TODO 모임 종료 · 평가 가능 알림(체크인한 사람)
    }

    private void insertNoShows(Gathering gathering) {
        List<Long> noShowParticipationIds = checkInRepository.insertNoShows(gathering.getId());
        // TODO 노쇼 매너온도 하락(점수 미정) — noShowParticipationIds 는 이번에 새로 노쇼가 된 참여만 담는다
    }

    /** 후보 조회 뒤 삭제된 모임은 null */
    private Gathering lock(Long gatheringId) {
        return gatheringRepository.findActiveByIdForUpdate(gatheringId).orElse(null);
    }
}
