package com.buddygo.buddygoserver.domain.buddy.controller;

import com.buddygo.buddygoserver.domain.buddy.api.BuddyRequestApi;
import com.buddygo.buddygoserver.domain.buddy.dto.request.BuddyRequestCreateRequest;
import com.buddygo.buddygoserver.domain.buddy.dto.response.BuddyRequestResponse;
import com.buddygo.buddygoserver.domain.buddy.dto.response.ReceivedBuddyRequestResponse;
import com.buddygo.buddygoserver.domain.buddy.service.BuddyRequestCommandService;
import com.buddygo.buddygoserver.domain.buddy.service.BuddyRequestQueryService;
import com.buddygo.buddygoserver.global.response.success.SuccessResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class BuddyRequestController implements BuddyRequestApi {

    private final BuddyRequestCommandService buddyRequestCommandService;
    private final BuddyRequestQueryService buddyRequestQueryService;

    @Override
    public ResponseEntity<SuccessResponse<BuddyRequestResponse>> send(Long userId, BuddyRequestCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(SuccessResponse.created(
                buddyRequestCommandService.send(userId, request.receiverId())));
    }

    @Override
    public ResponseEntity<SuccessResponse<List<ReceivedBuddyRequestResponse>>> getReceived(Long userId) {
        return ResponseEntity.ok(SuccessResponse.from(buddyRequestQueryService.getReceived(userId)));
    }

    @Override
    public ResponseEntity<SuccessResponse<BuddyRequestResponse>> cancel(Long userId, Long requestId) {
        return ResponseEntity.ok(SuccessResponse.from(buddyRequestCommandService.cancel(userId, requestId)));
    }

    @Override
    public ResponseEntity<SuccessResponse<BuddyRequestResponse>> accept(Long userId, Long requestId) {
        return ResponseEntity.ok(SuccessResponse.from(buddyRequestCommandService.accept(userId, requestId)));
    }

    @Override
    public ResponseEntity<SuccessResponse<BuddyRequestResponse>> reject(Long userId, Long requestId) {
        return ResponseEntity.ok(SuccessResponse.from(buddyRequestCommandService.reject(userId, requestId)));
    }
}
