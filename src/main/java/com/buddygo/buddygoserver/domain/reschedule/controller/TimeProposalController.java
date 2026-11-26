package com.buddygo.buddygoserver.domain.reschedule.controller;

import com.buddygo.buddygoserver.domain.reschedule.api.TimeProposalApi;
import com.buddygo.buddygoserver.domain.reschedule.dto.request.TimeProposalCreateRequest;
import com.buddygo.buddygoserver.domain.reschedule.dto.response.TimeProposalResponse;
import com.buddygo.buddygoserver.domain.reschedule.service.TimeProposalCommandService;
import com.buddygo.buddygoserver.domain.reschedule.service.TimeProposalQueryService;
import com.buddygo.buddygoserver.global.response.success.SuccessResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class TimeProposalController implements TimeProposalApi {

    private final TimeProposalCommandService commandService;
    private final TimeProposalQueryService queryService;

    @Override
    public ResponseEntity<SuccessResponse<TimeProposalResponse>> propose(Long userId, Long gatheringId,
                                                                         TimeProposalCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(SuccessResponse.created(commandService.propose(userId, gatheringId, request)));
    }

    @Override
    public ResponseEntity<SuccessResponse<TimeProposalResponse>> current(Long userId, Long gatheringId) {
        return ResponseEntity.ok(SuccessResponse.from(queryService.current(userId, gatheringId)));
    }

    @Override
    public ResponseEntity<SuccessResponse<TimeProposalResponse>> accept(Long userId, Long gatheringId, Long proposalId) {
        return ResponseEntity.ok(SuccessResponse.from(commandService.accept(userId, gatheringId, proposalId)));
    }

    @Override
    public ResponseEntity<SuccessResponse<TimeProposalResponse>> reject(Long userId, Long gatheringId, Long proposalId) {
        return ResponseEntity.ok(SuccessResponse.from(commandService.reject(userId, gatheringId, proposalId)));
    }

    @Override
    public ResponseEntity<SuccessResponse<TimeProposalResponse>> withdraw(Long userId, Long gatheringId,
                                                                          Long proposalId) {
        return ResponseEntity.ok(SuccessResponse.from(commandService.withdraw(userId, gatheringId, proposalId)));
    }
}
