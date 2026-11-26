package com.buddygo.buddygoserver.domain.reschedule.service;

import com.buddygo.buddygoserver.domain.application.entity.Participation;
import com.buddygo.buddygoserver.domain.application.repository.ParticipationRepository;
import com.buddygo.buddygoserver.domain.gathering.entity.Gathering;
import com.buddygo.buddygoserver.domain.gathering.exception.GatheringErrorType;
import com.buddygo.buddygoserver.domain.gathering.repository.GatheringRepository;
import com.buddygo.buddygoserver.domain.gathering.service.GatheringScheduleService;
import com.buddygo.buddygoserver.domain.reschedule.dto.request.TimeProposalCreateRequest;
import com.buddygo.buddygoserver.domain.reschedule.dto.response.TimeProposalResponse;
import com.buddygo.buddygoserver.domain.reschedule.entity.TimeProposal;
import com.buddygo.buddygoserver.domain.reschedule.entity.TimeProposalVote;
import com.buddygo.buddygoserver.domain.reschedule.enums.TimeProposalStatus;
import com.buddygo.buddygoserver.domain.reschedule.exception.TimeProposalErrorType;
import com.buddygo.buddygoserver.domain.reschedule.repository.TimeProposalRepository;
import com.buddygo.buddygoserver.domain.reschedule.repository.TimeProposalVoteRepository;
import com.buddygo.buddygoserver.domain.user.entity.User;
import com.buddygo.buddygoserver.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * 시간 변경 제안. 확정 참가자(호스트 제외)가 있는 모임의 시작 시각은 제안 시점 구성원(호스트 포함 확정 참가자) 모두가
 * 수락해야 바뀐다. 모든 쓰기는 모임 행을 먼저 잠그고(통과하면 모임의 시작 시각을 바꾼다) 제안·투표는 그 뒤에 읽는다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class TimeProposalCommandService {

    private final GatheringRepository gatheringRepository;
    private final ParticipationRepository participationRepository;
    private final TimeProposalRepository timeProposalRepository;
    private final TimeProposalVoteRepository timeProposalVoteRepository;
    private final TimeProposalPolicy policy;
    private final TimeProposalNotifier notifier;
    private final TimeProposalCloser closer;
    private final GatheringScheduleService gatheringScheduleService;
    private final Clock clock;

    /** 제안. 제안자는 수락한 것으로 치고, 나머지 구성원에게 알린다. 기한이 지났는데 아직 만료 처리되지 않은 제안은 여기서 만료시킨다. */
    public TimeProposalResponse propose(Long userId, Long gatheringId, TimeProposalCreateRequest request) {
        Instant now = Instant.now(clock);
        Gathering gathering = lockGathering(gatheringId);
        if (gathering.isCanceled()) {
            throw new CustomException(GatheringErrorType.ALREADY_CANCELED);
        }
        if (gathering.hasStarted(now)) {
            throw new CustomException(GatheringErrorType.ALREADY_STARTED);
        }
        List<Participation> members = participationRepository.findConfirmedWithUser(gatheringId);
        User proposer = members.stream().map(Participation::getUser).filter(u -> u.getId().equals(userId)).findFirst()
                .orElseThrow(() -> new CustomException(TimeProposalErrorType.NOT_MEMBER));
        if (members.size() < 2) {
            throw new CustomException(TimeProposalErrorType.NO_CONFIRMED_PARTICIPANTS);
        }
        TimeProposal existing = timeProposalRepository.findPending(gatheringId).orElse(null);
        if (existing != null) {
            if (!existing.isExpiredAt(now)) {
                throw new CustomException(TimeProposalErrorType.ALREADY_PENDING);
            }
            closer.expire(existing, gathering, now);
            // IDENTITY 라 새 제안은 바로 INSERT 된다. 만료를 먼저 반영해야 진행 중 제안 유니크 인덱스에 걸리지 않는다
            timeProposalRepository.flush();
        }
        policy.validate(gathering, request.startAt(), now);

        TimeProposal proposal = timeProposalRepository.saveAndFlush(
                TimeProposal.propose(gathering, proposer, request.startAt(), policy.expiresAt(gathering, request.startAt())));
        List<TimeProposalVote> votes = timeProposalVoteRepository.saveAll(members.stream()
                .map(p -> TimeProposalVote.of(proposal, p.getUser(), p.getUser().getId().equals(userId), now))
                .toList());
        notifier.proposed(proposal, votes);
        return TimeProposalResponse.of(proposal, votes);
    }

    /** 수락. 마지막 수락이면 시작 시각을 바꾸고 대기 신청을 자동 거절한다. */
    public TimeProposalResponse accept(Long userId, Long gatheringId, Long proposalId) {
        return respond(userId, gatheringId, proposalId, true);
    }

    /** 거절. 한 명이라도 거절하면 바로 무산된다. */
    public TimeProposalResponse reject(Long userId, Long gatheringId, Long proposalId) {
        return respond(userId, gatheringId, proposalId, false);
    }

    /** 철회. 제안자만, 기한 전까지. */
    public TimeProposalResponse withdraw(Long userId, Long gatheringId, Long proposalId) {
        Instant now = Instant.now(clock);
        lockGathering(gatheringId);
        TimeProposal proposal = pendingProposal(gatheringId, proposalId, now);
        if (!proposal.isProposedBy(userId)) {
            throw new CustomException(TimeProposalErrorType.NOT_PROPOSER);
        }
        List<TimeProposalVote> votes = timeProposalVoteRepository.findWithUser(proposalId);
        proposal.resolve(TimeProposalStatus.WITHDRAWN, now);
        notifier.withdrawn(proposal, votes);
        return TimeProposalResponse.of(proposal, votes);
    }

    private TimeProposalResponse respond(Long userId, Long gatheringId, Long proposalId, boolean accept) {
        Instant now = Instant.now(clock);
        Gathering gathering = lockGathering(gatheringId);
        TimeProposal proposal = pendingProposal(gatheringId, proposalId, now);
        List<TimeProposalVote> votes = timeProposalVoteRepository.findWithUser(proposalId);
        TimeProposalVote mine = votes.stream().filter(v -> v.getUser().getId().equals(userId)).findFirst()
                .orElseThrow(() -> new CustomException(TimeProposalErrorType.NOT_MEMBER));
        // 명단이 바뀌면 이벤트로 무산되지만, 놓친 경로가 있어도 동의하지 않은 사람의 시각이 바뀌지 않게 여기서도 확인한다
        if (closer.cancelIfRosterChanged(proposal, gathering, votes, null, now)) {
            return TimeProposalResponse.of(proposal, votes);
        }
        if (!mine.isPending()) {
            throw new CustomException(TimeProposalErrorType.ALREADY_RESPONDED);
        }

        mine.decide(accept, now);
        if (!accept) {
            proposal.resolve(TimeProposalStatus.REJECTED, now);
            notifier.rejected(proposal, votes, userId);
        } else if (votes.stream().allMatch(TimeProposalVote::isAccepted)) {
            proposal.resolve(TimeProposalStatus.APPROVED, now);
            gatheringScheduleService.changeStartAt(gathering, proposal.getToStartAt(), now);
            notifier.approved(proposal, votes, userId);
        }
        return TimeProposalResponse.of(proposal, votes);
    }

    private Gathering lockGathering(Long gatheringId) {
        return gatheringRepository.findByIdForUpdate(gatheringId)
                .orElseThrow(() -> new CustomException(GatheringErrorType.NOT_FOUND));
    }

    /** 모임 행을 잠근 뒤에 읽는다. 만료 처리 전이라도 기한이 지났으면 응답·철회할 수 없다(만료 작업이 정리한다). */
    private TimeProposal pendingProposal(Long gatheringId, Long proposalId, Instant now) {
        TimeProposal proposal = timeProposalRepository.findInGathering(proposalId, gatheringId)
                .orElseThrow(() -> new CustomException(TimeProposalErrorType.NOT_FOUND));
        if (!proposal.isPending()) {
            throw new CustomException(TimeProposalErrorType.NOT_PENDING);
        }
        if (proposal.isExpiredAt(now)) {
            throw new CustomException(TimeProposalErrorType.EXPIRED);
        }
        return proposal;
    }
}
