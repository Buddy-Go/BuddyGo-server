package com.buddygo.buddygoserver.domain.notice.controller;

import com.buddygo.buddygoserver.domain.notice.api.NoticeApi;
import com.buddygo.buddygoserver.domain.notice.dto.request.NoticeCreateRequest;
import com.buddygo.buddygoserver.domain.notice.dto.request.NoticeUpdateRequest;
import com.buddygo.buddygoserver.domain.notice.dto.response.NoticeListResponse;
import com.buddygo.buddygoserver.domain.notice.dto.response.NoticeResponse;
import com.buddygo.buddygoserver.domain.notice.service.NoticeCommandService;
import com.buddygo.buddygoserver.domain.notice.service.NoticeQueryService;
import com.buddygo.buddygoserver.global.response.success.SuccessResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class NoticeController implements NoticeApi {
    private final NoticeCommandService commandService;
    private final NoticeQueryService queryService;

    @Override
    public ResponseEntity<SuccessResponse<NoticeListResponse>> list(Long userId, Long gatheringId, int page, int size) {
        return ResponseEntity.ok(SuccessResponse.from(queryService.list(userId, gatheringId, page, size)));
    }
    @Override
    public ResponseEntity<SuccessResponse<NoticeResponse>> detail(Long userId, Long gatheringId, Long noticeId) {
        return ResponseEntity.ok(SuccessResponse.from(queryService.detail(userId, gatheringId, noticeId)));
    }
    @Override
    public ResponseEntity<SuccessResponse<NoticeResponse>> create(Long userId, Long gatheringId, NoticeCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(SuccessResponse.created(commandService.create(userId, gatheringId, request)));
    }
    @Override
    public ResponseEntity<SuccessResponse<NoticeResponse>> update(Long userId, Long gatheringId, Long noticeId, NoticeUpdateRequest request) {
        return ResponseEntity.ok(SuccessResponse.from(commandService.update(userId, gatheringId, noticeId, request)));
    }
    @Override
    public ResponseEntity<SuccessResponse<Void>> delete(Long userId, Long gatheringId, Long noticeId) {
        commandService.delete(userId, gatheringId, noticeId);
        return ResponseEntity.ok(SuccessResponse.ok());
    }
}
