package com.buddygo.buddygoserver.domain.gathering.service;

import com.buddygo.buddygoserver.domain.application.enums.RejectReason;
import com.buddygo.buddygoserver.domain.application.repository.ParticipationRepository;
import com.buddygo.buddygoserver.domain.gathering.entity.Gathering;
import com.buddygo.buddygoserver.domain.notification.enums.NotificationType;
import com.buddygo.buddygoserver.domain.notification.service.NotificationSender;
import com.buddygo.buddygoserver.domain.notification.support.NotificationMessages;
import com.buddygo.buddygoserver.domain.notification.support.NotificationTarget;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * 모임 일시 변경의 공통 처리. 호스트 수정(확정 참가자 0명)과 시간 변경 제안 통과가 같이 쓴다.
 * 모임 행을 잠근 트랜잭션 안에서만 부른다.
 */
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class GatheringScheduleService {

    private final ParticipationRepository participationRepository;
    private final NotificationSender notificationSender;

    /** 시작 시각을 바꾼다(진행 시간은 그대로). 바뀌었으면 대기 신청을 자동 거절한다. */
    public void changeStartAt(Gathering gathering, Instant startAt, Instant now) {
        if (gathering.changeSchedule(gathering.getPlace(), startAt, gathering.getDuration())) {
            rejectPendingOnScheduleChange(gathering, now);
        }
    }

    /**
     * 일시·장소가 바뀌면 대기 신청은 바뀌기 전 정보를 보고 낸 것이므로 자동 거절한다. 신청자가 바뀐 일정을 확인하기 전에
     * 호스트가 수락해 동의 없이 확정되는 일을 막는다(모집중이면 다시 신청할 수 있다).
     */
    public void rejectPendingOnScheduleChange(Gathering gathering, Instant now) {
        List<Long> pending = participationRepository.findPendingUserIds(gathering.getId());
        if (pending.isEmpty()) {
            return;
        }
        participationRepository.rejectAllPending(gathering.getId(), RejectReason.SCHEDULE_CHANGED, now);
        notificationSender.send(NotificationType.APPLICATION_REJECTED, pending, null,
                NotificationTarget.gathering(gathering.getId()),
                NotificationMessages.applicationRejectedScheduleChanged(gathering.getTitle()));
    }
}
