package com.buddygo.buddygoserver.domain.reschedule.dto.response;

import com.buddygo.buddygoserver.domain.reschedule.entity.TimeProposal;
import com.buddygo.buddygoserver.domain.reschedule.entity.TimeProposalVote;
import com.buddygo.buddygoserver.domain.reschedule.enums.TimeProposalStatus;
import com.buddygo.buddygoserver.domain.reschedule.enums.VoteDecision;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

public record TimeProposalResponse(
        @Schema(example = "7") Long proposalId,
        @Schema(example = "PENDING") TimeProposalStatus status,
        @Schema(example = "2") Long proposerId,
        @Schema(example = "운동메이트") String proposerNickname,
        @Schema(description = "제안할 때의 시작 시각") Instant fromStartAt,
        @Schema(description = "제안한 시작 시각") Instant toStartAt,
        @Schema(description = "응답 기한. 이때까지 모두 수락하지 않으면 무산된다") Instant expiresAt,
        Instant createdAt,
        @Schema(description = "끝난 시각. 진행 중이면 null") Instant resolvedAt,
        @Schema(description = "제안 시점 구성원의 응답. 호스트가 먼저") List<VoteResponse> votes
) {

    public static TimeProposalResponse of(TimeProposal proposal, List<TimeProposalVote> votes) {
        Long hostId = proposal.getGathering().getHost().getId();
        return new TimeProposalResponse(proposal.getId(), proposal.getStatus(), proposal.getProposer().getId(),
                proposal.getProposer().getNickname(), proposal.getFromStartAt(), proposal.getToStartAt(),
                proposal.getExpiresAt(), proposal.getCreatedAt(), proposal.getResolvedAt(),
                votes.stream()
                        .sorted(Comparator.comparing((TimeProposalVote v) -> !v.getUser().getId().equals(hostId))
                                .thenComparing(TimeProposalVote::getId))
                        .map(v -> VoteResponse.of(v, hostId))
                        .toList());
    }

    public record VoteResponse(
            @Schema(example = "2") Long userId,
            @Schema(example = "운동메이트") String nickname,
            @Schema(example = "false") boolean host,
            @Schema(example = "PENDING") VoteDecision decision,
            Instant respondedAt
    ) {
        static VoteResponse of(TimeProposalVote vote, Long hostId) {
            return new VoteResponse(vote.getUser().getId(), vote.getUser().getNickname(),
                    vote.getUser().getId().equals(hostId), vote.getDecision(), vote.getRespondedAt());
        }
    }
}
