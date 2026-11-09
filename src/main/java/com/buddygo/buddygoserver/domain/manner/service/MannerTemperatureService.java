package com.buddygo.buddygoserver.domain.manner.service;

import com.buddygo.buddygoserver.domain.manner.entity.MannerTemperatureLog;
import com.buddygo.buddygoserver.domain.manner.repository.MannerTemperatureLogRepository;
import com.buddygo.buddygoserver.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 매너온도 변경. 점수에 곡선을 적용해 더하고 사유와 함께 이력을 남긴다.
 * 이력의 delta 는 실제로 더한 값이라 시작값 + 이력 합 = 현재 온도다.
 *
 * <p>평가·모임은 ID 로만 받는다. 이 도메인이 부르는 쪽(review·checkin·gathering)을 참조하지 않게 하려는 것이다.
 */
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class MannerTemperatureService {

    private final MannerTemperatureLogRepository mannerTemperatureLogRepository;
    private final MannerTemperatureCurve curve;

    /**
     * @param lockedTarget 이 트랜잭션에서 행을 잠근(findByIdForUpdate) 사용자. 잠그지 않고 읽은 값에 더하면 동시 평가 하나가 사라진다.
     * @return 실제로 더한 변화량
     */
    public BigDecimal applyReview(User lockedTarget, Long reviewId, BigDecimal score) {
        BigDecimal delta = curve.deltaOf(lockedTarget.getExactMannerTemperature(), score);
        lockedTarget.applyMannerDelta(delta);
        mannerTemperatureLogRepository.save(MannerTemperatureLog.ofReview(lockedTarget, delta, reviewId));
        return delta;
    }
}
