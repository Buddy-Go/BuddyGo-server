package com.buddygo.buddygoserver.domain.reschedule.api;

import com.buddygo.buddygoserver.domain.reschedule.dto.request.TimeProposalCreateRequest;
import com.buddygo.buddygoserver.domain.reschedule.dto.response.TimeProposalResponse;
import com.buddygo.buddygoserver.global.response.success.SuccessResponse;
import com.buddygo.buddygoserver.global.security.LoginUserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@Tag(name = "time-proposal", description = """
        시작 시각 변경 제안. 확정 참가자(호스트 제외)가 있는 모임은 호스트가 혼자 시각을 바꿀 수 없고,
        제안 시점의 구성원(호스트 포함 확정 참가자) 모두가 수락해야 바뀐다.""")
@RequestMapping("/api/gatherings/{gatheringId}/time-proposals")
@ApiResponses({
        @ApiResponse(responseCode = "401", description = "G004 토큰 없음 · A002 유효하지 않음 · A003 만료"),
        @ApiResponse(responseCode = "403", description = "TP002 호스트·확정 참가자(제안 시점 구성원)가 아님"),
        @ApiResponse(responseCode = "404", description = "GA001 없거나 삭제된 모임 · TP001 해당 모임에 없는 제안")
})
public interface TimeProposalApi {

    @Operation(summary = "시간 변경 제안", description = """
            호스트 또는 확정 참가자가 새 시작 시각을 제안한다. 제안자는 수락한 것으로 치고 나머지 구성원에게 알림이 간다.
            - 확정 참가자(호스트 제외)가 1명 이상이고, 한 모임에 진행 중인 제안은 하나
            - 새 시작 시각: 10분 단위, 지금 시작 시각과 다르고 앞뒤 1시간 안. 진행 시간은 그대로
            - 마감: 체크인이 열리기 전(시작 10분 전)까지, 새 시작은 지금부터 30분 이후
            - 모집중: 데드라인 전까지, 새 시작도 데드라인이 지나지 않는 시각(지금부터 1시간 30분 이후)
            응답 기한(expiresAt)은 위 조건이 깨지는 가장 이른 시각이다. 그때까지 모두 수락하지 않으면 무산된다.""")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "제안 성공"),
            @ApiResponse(responseCode = "400", description = "G002 입력값 · TP005 제안할 수 없는 시작 시각"),
            @ApiResponse(responseCode = "409", description = "TP003 확정 참가자 없음(모임 수정 사용) · TP004 진행 중인 제안 있음 · TP006 기한 지남 · GA008 이미 시작 · GA009 이미 취소")
    })
    @PostMapping
    ResponseEntity<SuccessResponse<TimeProposalResponse>> propose(@LoginUserId Long userId,
            @PathVariable Long gatheringId, @Valid @RequestBody TimeProposalCreateRequest request);

    @Operation(summary = "진행 중인 제안", description = "호스트와 확정 참가자만. 진행 중인 제안이 없으면 data 가 null 이다.")
    @GetMapping("/current")
    ResponseEntity<SuccessResponse<TimeProposalResponse>> current(@LoginUserId Long userId,
            @PathVariable Long gatheringId);

    @Operation(summary = "수락", description = """
            제안 시점 구성원만. 모두 수락하면 즉시 시작 시각이 바뀌고(status=APPROVED) 대기 신청은 자동 거절된다
            (rejectReason=SCHEDULE_CHANGED). 데드라인은 새 시작 시각 기준으로 다시 계산된다.
            그사이 확정 명단이 바뀌었으면 제안은 무산되고 status=CANCELED 로 돌려준다.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "응답 반영"),
            @ApiResponse(responseCode = "409", description = "TP007 이미 끝난 제안 · TP008 응답 기한 지남 · TP009 이미 응답함")
    })
    @PostMapping("/{proposalId}/accept")
    ResponseEntity<SuccessResponse<TimeProposalResponse>> accept(@LoginUserId Long userId,
            @PathVariable Long gatheringId, @PathVariable Long proposalId);

    @Operation(summary = "거절", description = "제안 시점 구성원만. 한 명이라도 거절하면 바로 무산된다(status=REJECTED).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "응답 반영"),
            @ApiResponse(responseCode = "409", description = "TP007 이미 끝난 제안 · TP008 응답 기한 지남 · TP009 이미 응답함")
    })
    @PostMapping("/{proposalId}/reject")
    ResponseEntity<SuccessResponse<TimeProposalResponse>> reject(@LoginUserId Long userId,
            @PathVariable Long gatheringId, @PathVariable Long proposalId);

    @Operation(summary = "철회", description = "제안한 사람만, 응답 기한 전까지(status=WITHDRAWN).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "철회 성공"),
            @ApiResponse(responseCode = "403", description = "TP010 제안한 사람이 아님"),
            @ApiResponse(responseCode = "409", description = "TP007 이미 끝난 제안 · TP008 응답 기한 지남")
    })
    @DeleteMapping("/{proposalId}")
    ResponseEntity<SuccessResponse<TimeProposalResponse>> withdraw(@LoginUserId Long userId,
            @PathVariable Long gatheringId, @PathVariable Long proposalId);
}
