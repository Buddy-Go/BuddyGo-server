package com.buddygo.buddygoserver.domain.application.controller;

import com.buddygo.buddygoserver.domain.application.api.ApplicationApi;
import com.buddygo.buddygoserver.domain.application.dto.request.ApplyRequest;
import com.buddygo.buddygoserver.domain.application.dto.response.ApplicantsResponse;
import com.buddygo.buddygoserver.domain.application.dto.response.ApplicationResponse;
import com.buddygo.buddygoserver.domain.application.service.ApplicationCommandService;
import com.buddygo.buddygoserver.domain.application.service.ApplicationQueryService;
import com.buddygo.buddygoserver.global.response.success.SuccessResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ApplicationController implements ApplicationApi {

    private final ApplicationCommandService applicationCommandService;
    private final ApplicationQueryService applicationQueryService;

    @Override
    public ResponseEntity<SuccessResponse<ApplicationResponse>> apply(Long userId, Long gatheringId, ApplyRequest request) {
        String message = request == null ? null : request.message();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(SuccessResponse.created(applicationCommandService.apply(userId, gatheringId, message)));
    }

    @Override
    public ResponseEntity<SuccessResponse<Void>> cancelMine(Long userId, Long gatheringId) {
        applicationCommandService.cancelMine(userId, gatheringId);
        return ResponseEntity.ok(SuccessResponse.ok());
    }

    @Override
    public ResponseEntity<SuccessResponse<ApplicantsResponse>> getApplicants(Long userId, Long gatheringId) {
        return ResponseEntity.ok(SuccessResponse.from(applicationQueryService.getApplicants(userId, gatheringId)));
    }

    @Override
    public ResponseEntity<SuccessResponse<ApplicationResponse>> accept(Long userId, Long gatheringId, Long applicationId) {
        return ResponseEntity.ok(SuccessResponse.from(applicationCommandService.accept(userId, gatheringId, applicationId)));
    }

    @Override
    public ResponseEntity<SuccessResponse<ApplicationResponse>> reject(Long userId, Long gatheringId, Long applicationId) {
        return ResponseEntity.ok(SuccessResponse.from(applicationCommandService.reject(userId, gatheringId, applicationId)));
    }

    @Override
    public ResponseEntity<SuccessResponse<ApplicationResponse>> leave(Long userId, Long gatheringId) {
        return ResponseEntity.ok(SuccessResponse.from(applicationCommandService.leave(userId, gatheringId)));
    }
}
