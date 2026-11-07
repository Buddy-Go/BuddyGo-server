package com.buddygo.buddygoserver.domain.notice.api;

import com.buddygo.buddygoserver.domain.notice.dto.request.CommentCreateRequest;
import com.buddygo.buddygoserver.domain.notice.dto.response.CommentListResponse;
import com.buddygo.buddygoserver.domain.notice.dto.response.CommentResponse;
import com.buddygo.buddygoserver.global.response.success.SuccessResponse;
import com.buddygo.buddygoserver.global.security.LoginUserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Tag(name = "notice-comment", description = "모임 공지 댓글. 호스트와 확정 참가자만 접근하며 나간 사람은 접근할 수 없다.")
@RequestMapping("/api/gatherings/{gatheringId}/notices")
@ApiResponses({
        @ApiResponse(responseCode = "401", description = "G004 토큰 없음 · A002 유효하지 않음 · A003 만료"),
        @ApiResponse(responseCode = "403", description = "N004 호스트·확정 참가자가 아님 · GA002 호스트 전용 작업"),
        @ApiResponse(responseCode = "404", description = "GA001 없거나 삭제된 모임 · N001 해당 모임에 없는 공지 · N002 해당 공지에 없는 댓글"),
        @ApiResponse(responseCode = "400", description = "G002 입력값 검증 실패"),
        @ApiResponse(responseCode = "409", description = "N005 종료·취소·자동 취소 대상 모임은 쓰기 불가(읽기는 가능)")
})
public interface NoticeCommentApi {
    @Operation(summary = "댓글 목록", description = "작성순(createdAt·id 오름차순). page는 0부터, size 1~100(기본 20). 대댓글은 지원하지 않는다.")
    @GetMapping("/{noticeId}/comments")
    ResponseEntity<SuccessResponse<CommentListResponse>> comments(@LoginUserId Long userId,
            @PathVariable Long gatheringId, @PathVariable Long noticeId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size);

    @Operation(summary = "댓글 작성", description = "내용 1~200자(기존 DB 기준), 공백 불가. 호스트와 해당 공지에 댓글을 남긴 현재 확정 참가자에게 알림. 작성자는 제외한다.")
    @ApiResponse(responseCode = "201", description = "작성 성공")
    @PostMapping("/{noticeId}/comments")
    ResponseEntity<SuccessResponse<CommentResponse>> comment(@LoginUserId Long userId,
            @PathVariable Long gatheringId, @PathVariable Long noticeId, @Valid @RequestBody CommentCreateRequest request);

    @Operation(summary = "댓글 삭제", description = "접근 가능한 작성자 본인 또는 호스트만. 종료·취소된 모임에서는 삭제도 불가.")
    @ApiResponse(responseCode = "403", description = "N003 작성자 본인·호스트가 아님 · N004 모임 접근 불가")
    @DeleteMapping("/{noticeId}/comments/{commentId}")
    ResponseEntity<SuccessResponse<Void>> deleteComment(@LoginUserId Long userId,
            @PathVariable Long gatheringId, @PathVariable Long noticeId, @PathVariable Long commentId);
}
