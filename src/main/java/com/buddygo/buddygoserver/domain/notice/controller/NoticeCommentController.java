package com.buddygo.buddygoserver.domain.notice.controller;

import com.buddygo.buddygoserver.domain.notice.api.NoticeCommentApi;
import com.buddygo.buddygoserver.domain.notice.dto.request.CommentCreateRequest;
import com.buddygo.buddygoserver.domain.notice.dto.response.CommentListResponse;
import com.buddygo.buddygoserver.domain.notice.dto.response.CommentResponse;
import com.buddygo.buddygoserver.domain.notice.service.NoticeCommentCommandService;
import com.buddygo.buddygoserver.domain.notice.service.NoticeCommentQueryService;
import com.buddygo.buddygoserver.global.response.success.SuccessResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class NoticeCommentController implements NoticeCommentApi {
    private final NoticeCommentCommandService commandService;
    private final NoticeCommentQueryService queryService;

    @Override
    public ResponseEntity<SuccessResponse<CommentListResponse>> comments(Long userId, Long gatheringId, Long noticeId, int page, int size) {
        return ResponseEntity.ok(SuccessResponse.from(queryService.comments(userId, gatheringId, noticeId, page, size)));
    }
    @Override
    public ResponseEntity<SuccessResponse<CommentResponse>> comment(Long userId, Long gatheringId, Long noticeId, CommentCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(SuccessResponse.created(commandService.comment(userId, gatheringId, noticeId, request)));
    }
    @Override
    public ResponseEntity<SuccessResponse<Void>> deleteComment(Long userId, Long gatheringId, Long noticeId, Long commentId) {
        commandService.deleteComment(userId, gatheringId, noticeId, commentId);
        return ResponseEntity.ok(SuccessResponse.ok());
    }
}
