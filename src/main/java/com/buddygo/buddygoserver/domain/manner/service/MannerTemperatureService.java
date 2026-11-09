package com.buddygo.buddygoserver.domain.manner.service;

import com.buddygo.buddygoserver.domain.manner.config.MannerTemperatureProperties;
import com.buddygo.buddygoserver.domain.manner.entity.MannerTemperatureLog;
import com.buddygo.buddygoserver.domain.manner.enums.MannerTemperatureReason;
import com.buddygo.buddygoserver.domain.manner.repository.MannerTemperatureLogRepository;
import com.buddygo.buddygoserver.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;

/**
 * 매너온도 변경. 점수에 곡선을 적용해 더하고 사유와 함께 이력을 남긴다.
 * 이력의 delta 는 실제로 더한 값이라 시작값 + 이력 합 = 현재 온도다.
 *
 * <p>모든 메서드는 이 트랜잭션에서 행을 잠근(findByIdForUpdate) 사용자를 받는다. 잠그지 않고 읽은 값에 더하면
 * 동시에 일어난 다른 변경 하나가 사라진다. 모임이 걸린 변경은 모임 행 → 사용자 행 순서로 잠근다.
 *
 * <p>평가·모임은 ID 로만 받는다. 이 도메인이 부르는 쪽(review·checkin·gathering)을 참조하지 않게 하려는 것이다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class MannerTemperatureService {

    private final MannerTemperatureLogRepository mannerTemperatureLogRepository;
    private final MannerTemperatureCurve curve;
    private final MannerTemperatureProperties properties;
    private final Clock clock;

    /** @return 실제로 더한 변화량 */
    public BigDecimal applyReview(User lockedTarget, Long reviewId, BigDecimal score) {
        BigDecimal delta = curve.deltaOf(lockedTarget.getExactMannerTemperature(), score);
        lockedTarget.applyMannerDelta(delta);
        mannerTemperatureLogRepository.save(MannerTemperatureLog.ofReview(lockedTarget, delta, reviewId));
        return delta;
    }

    /** 노쇼 페널티. 모임마다 한 번만 부른다(새로 노쇼가 된 사람만). */
    public BigDecimal applyNoShow(User lockedUser, Long gatheringId) {
        return applyGatheringChange(lockedUser, gatheringId, MannerTemperatureReason.NO_SHOW, penalty().noShow());
    }

    /** 지각 페널티. 지각 체크인이 저장된 트랜잭션에서 부른다. */
    public BigDecimal applyLate(User lockedUser, Long gatheringId) {
        return applyGatheringChange(lockedUser, gatheringId, MannerTemperatureReason.LATE, penalty().late());
    }

    /**
     * 마감된 모임에서 나간 확정 참가자 페널티. 데드라인 후(방폭)가 더 크다. 모집중인 모임에서 나가면 부르지 않는다.
     * 데드라인 전 나가기는 다시 들어와 또 나가면 그때마다 들어간다.
     */
    public BigDecimal applyLeave(User lockedUser, Long gatheringId, boolean afterDeadline) {
        return afterDeadline
                ? applyGatheringChange(lockedUser, gatheringId, MannerTemperatureReason.LEFT_AFTER_DEADLINE,
                        penalty().leaveAfterDeadline())
                : applyGatheringChange(lockedUser, gatheringId, MannerTemperatureReason.LEFT_BEFORE_DEADLINE,
                        penalty().leaveBeforeDeadline());
    }

    /** 호스트 취소 페널티. 마감 후(정원이 찬 상태) 취소가 모집중 취소보다 크다. */
    public BigDecimal applyHostCancel(User lockedHost, Long gatheringId, boolean wasClosed) {
        return wasClosed
                ? applyGatheringChange(lockedHost, gatheringId, MannerTemperatureReason.HOST_CANCELED_CLOSED,
                        penalty().hostCancelClosed())
                : applyGatheringChange(lockedHost, gatheringId, MannerTemperatureReason.HOST_CANCELED_RECRUITING,
                        penalty().hostCancelRecruiting());
    }

    /**
     * 호스트 출석 보너스. 종료된 모임에서 호스트가 체크인(정상·지각)했으면 종료할 때 한 번 준다. 참가자가 모두 노쇼여도 준다.
     * 지각한 호스트는 지각 페널티와 보너스를 둘 다 받는다.
     */
    public BigDecimal applyHostAttendance(User lockedHost, Long gatheringId) {
        return applyGatheringChange(lockedHost, gatheringId, MannerTemperatureReason.HOST_ATTENDED,
                properties.bonus().hostAttendance());
    }

    private MannerTemperatureProperties.Penalty penalty() {
        return properties.penalty();
    }

    /**
     * 모임에 걸린 시스템 변경(페널티·보너스). 모임당 한 번뿐인 사유는 이력을 먼저 ON CONFLICT 로 넣고, 들어갔을 때만 온도에 더한다.
     * 같은 모임·사유의 이력이 이미 있으면(호출 경로가 두 번 부른 버그) 아무것도 바꾸지 않고 경고만 남긴다. 예외로 두면
     * 스케줄러에서는 그 모임의 노쇼 기록·종료까지 롤백되어 매 주기 실패한다.
     *
     * @return 실제로 더한 변화량(중복이면 0)
     */
    private BigDecimal applyGatheringChange(User lockedUser, Long gatheringId, MannerTemperatureReason reason,
                                             BigDecimal score) {
        BigDecimal delta = curve.deltaOf(lockedUser.getExactMannerTemperature(), score);
        if (reason.isOncePerGathering()) {
            boolean inserted = !mannerTemperatureLogRepository.insertOncePerGatheringIgnoringDuplicate(
                    lockedUser.getId(), delta, reason.name(), gatheringId, Instant.now(clock)).isEmpty();
            if (!inserted) {
                log.warn("[Manner Duplicate] {} user={} gathering={} 이미 반영됨, 건너뜀", reason, lockedUser.getId(), gatheringId);
                return BigDecimal.ZERO;
            }
        } else {
            mannerTemperatureLogRepository.save(MannerTemperatureLog.ofGathering(lockedUser, delta, reason, gatheringId));
        }
        lockedUser.applyMannerDelta(delta);
        return delta;
    }
}
