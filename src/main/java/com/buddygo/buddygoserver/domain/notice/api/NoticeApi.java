package com.buddygo.buddygoserver.domain.notice.api;

import com.buddygo.buddygoserver.domain.notice.dto.request.NoticeCreateRequest;
import com.buddygo.buddygoserver.domain.notice.dto.request.NoticeUpdateRequest;
import com.buddygo.buddygoserver.domain.notice.dto.response.NoticeListResponse;
import com.buddygo.buddygoserver.domain.notice.dto.response.NoticeResponse;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Tag(name = "notice", description = "모임 공지. 호스트와 확정 참가자만 접근하며 나간 사람은 접근할 수 없다.")
@RequestMapping("/api/gatherings/{gatheringId}/notices")
@ApiResponses({
        @ApiResponse(responseCode = "401", description = "G004 토큰 없음 · A002 유효하지 않음 · A003 만료"),
        @ApiResponse(responseCode = "403", description = "N004 호스트·확정 참가자가 아님 · GA002 호스트 전용 작업"),
        @ApiResponse(responseCode = "404", description = "GA001 없거나 삭제된 모임 · N001 해당 모임에 없는 공지 · N002 해당 공지에 없는 댓글"),
        @ApiResponse(responseCode = "400", description = "G002 입력값 검증 실패"),
        @ApiResponse(responseCode = "409", description = "N005 종료·취소·자동 취소 대상 모임은 쓰기 불가(읽기는 가능)")
})
public interface NoticeApi {
    @Operation(summary = "공지 목록", description = "최신 작성순(createdAt·id 내림차순). page는 0부터, size 1~100(기본 20), hasNext로 다음 페이지 확인.")
    @GetMapping
    ResponseEntity<SuccessResponse<NoticeListResponse>> list(@LoginUserId Long userId,
            @PathVariable Long gatheringId, @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size);

    @Operation(summary = "공지 상세", description = "edited와 editedAt으로 수정 여부를 표시한다.")
    @GetMapping("/{noticeId}")
    ResponseEntity<SuccessResponse<NoticeResponse>> detail(@LoginUserId Long userId,
            @PathVariable Long gatheringId, @PathVariable Long noticeId);

    @Operation(summary = "공지 작성", description = "호스트만. 제목 1~255자, 본문 1~200자(기존 DB 기준). 공백만 입력 불가. 확정 참가자에게 앱 알림 저장.")
    @ApiResponse(responseCode = "201", description = "작성 성공")
    @PostMapping
    ResponseEntity<SuccessResponse<NoticeResponse>> create(@LoginUserId Long userId,
            @PathVariable Long gatheringId, @Valid @RequestBody NoticeCreateRequest request);

    @Operation(summary = "공지 수정", description = "호스트만. 제목·본문 중 보낸 항목만 수정(null은 유지). 하나 이상 필수, 공백 불가. 확정 참가자에게 앱 알림 저장.")
    @PatchMapping("/{noticeId}")
    ResponseEntity<SuccessResponse<NoticeResponse>> update(@LoginUserId Long userId,
            @PathVariable Long gatheringId, @PathVariable Long noticeId, @Valid @RequestBody NoticeUpdateRequest request);

    @Operation(summary = "공지 삭제", description = "호스트만. 해당 공지의 댓글도 함께 삭제한다. 기존 알림은 공지 연결을 해제하여 보존한다.")
    @DeleteMapping("/{noticeId}")
    ResponseEntity<SuccessResponse<Void>> delete(@LoginUserId Long userId,
            @PathVariable Long gatheringId, @PathVariable Long noticeId);

}
