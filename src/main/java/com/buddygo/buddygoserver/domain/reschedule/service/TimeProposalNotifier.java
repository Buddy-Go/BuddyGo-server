package com.buddygo.buddygoserver.domain.reschedule.service;

import com.buddygo.buddygoserver.domain.reschedule.entity.TimeProposal;
import com.buddygo.buddygoserver.domain.reschedule.entity.TimeProposalVote;
import com.buddygo.buddygoserver.domain.notification.enums.NotificationType;
import com.buddygo.buddygoserver.domain.notification.service.NotificationSender;
import com.buddygo.buddygoserver.domain.notification.support.NotificationMessages;
import com.buddygo.buddygoserver.domain.notification.support.NotificationTarget;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;

/** 시간 변경 제안 알림. 받는 사람은 제안 시점 구성원(투표 행)이고, 행동한 사람은 NotificationSender 가 뺀다. */
@Component
@RequiredArgsConstructor
public class TimeProposalNotifier {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("M월 d일 HH:mm");

    private final NotificationSender notificationSender;
    private final Clock clock;

    public void proposed(TimeProposal proposal, List<TimeProposalVote> votes) {
        send(NotificationType.TIME_CHANGE_PROPOSED, voters(votes), proposal.getProposer().getId(), proposal,
                NotificationMessages.timeChangeProposed(title(proposal), proposal.getProposer().getNickname(),
                        format(proposal)));
    }

    public void approved(TimeProposal proposal, List<TimeProposalVote> votes, Long lastAccepterId) {
        send(NotificationType.TIME_CHANGE_APPROVED, voters(votes), lastAccepterId, proposal,
                NotificationMessages.timeChangeApproved(title(proposal), format(proposal)));
    }

    public void rejected(TimeProposal proposal, List<TimeProposalVote> votes, Long rejecterId) {
        send(NotificationType.TIME_CHANGE_REJECTED, voters(votes), rejecterId, proposal,
                NotificationMessages.timeChangeRejected(title(proposal)));
    }

    public void withdrawn(TimeProposal proposal, List<TimeProposalVote> votes) {
        send(NotificationType.TIME_CHANGE_CANCELED, voters(votes), proposal.getProposer().getId(), proposal,
                NotificationMessages.timeChangeWithdrawn(title(proposal)));
    }

    public void expired(TimeProposal proposal, List<TimeProposalVote> votes) {
        send(NotificationType.TIME_CHANGE_CANCELED, voters(votes), null, proposal,
                NotificationMessages.timeChangeExpired(title(proposal)));
    }

    /** 남은 구성원(제안 시점 구성원 중 아직 확정인 사람)에게만. 새로 수락된 사람은 제안을 본 적이 없다. */
    public void rosterChanged(TimeProposal proposal, Collection<Long> remainingVoters, Long actorId) {
        send(NotificationType.TIME_CHANGE_CANCELED, remainingVoters, actorId, proposal,
                NotificationMessages.timeChangeRosterChanged(title(proposal)));
    }

    private void send(NotificationType type, Collection<Long> receivers, Long actorId, TimeProposal proposal,
                      String message) {
        notificationSender.send(type, receivers, actorId, NotificationTarget.gathering(proposal.getGathering().getId()),
                message);
    }

    private static List<Long> voters(List<TimeProposalVote> votes) {
        return votes.stream().map(v -> v.getUser().getId()).toList();
    }

    private static String title(TimeProposal proposal) {
        return proposal.getGathering().getTitle();
    }

    private String format(TimeProposal proposal) {
        return TIME.format(proposal.getToStartAt().atZone(clock.getZone()));
    }
}
