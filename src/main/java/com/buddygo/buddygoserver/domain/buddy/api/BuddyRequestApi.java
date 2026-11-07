package com.buddygo.buddygoserver.domain.buddy.api;

import com.buddygo.buddygoserver.domain.buddy.dto.request.BuddyRequestCreateRequest;
import com.buddygo.buddygoserver.domain.buddy.dto.response.BuddyRequestResponse;
import com.buddygo.buddygoserver.domain.buddy.dto.response.ReceivedBuddyRequestResponse;
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

import java.util.List;

@Tag(name = "buddy", description = "버디(메이트) 신청 · 목록 · 함께 운동한 사람")
@ApiResponses(@ApiResponse(responseCode = "401", description = "G004 토큰 없음 · A002 유효하지 않음 · A003 만료"))
@RequestMapping("/api/buddy-requests")
public interface BuddyRequestApi {

    @Operation(summary = "버디 신청",
            description = """
                    함께 운동한 사람(같은 모임에서 둘 다 정상·지각 체크인)에게만 보낼 수 있다.
                    상대가 나에게 보낸 신청이 대기 중이면 그 신청이 수락되고 바로 버디가 된다(status=ACCEPTED, 그 신청의 ID).
                    거절·취소된 뒤에는 다시 보낼 수 있다.""")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "신청 성공(PENDING) 또는 맞신청으로 버디가 됨(ACCEPTED)"),
            @ApiResponse(responseCode = "400", description = "G002 receiverId 누락 · B006 자기 자신"),
            @ApiResponse(responseCode = "403", description = "B007 함께 운동한 사람이 아님"),
            @ApiResponse(responseCode = "404", description = "U001 없는 사용자"),
            @ApiResponse(responseCode = "409", description = "B002 이미 버디 · B003 이미 보낸 신청이 대기 중")
    })
    @PostMapping
    ResponseEntity<SuccessResponse<BuddyRequestResponse>> send(
            @LoginUserId Long userId, @Valid @RequestBody BuddyRequestCreateRequest request);

    @Operation(summary = "받은 버디 신청 목록",
            description = "대기 중인 받은 신청(최근 순). 보낸 사람의 닉네임·매너온도·참여 횟수(체크인한 모임 수)를 담는다. 개수는 목록 길이로 쓴다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/received")
    ResponseEntity<SuccessResponse<List<ReceivedBuddyRequestResponse>>> getReceived(@LoginUserId Long userId);

    @Operation(summary = "보낸 버디 신청 취소", description = "보낸 사람만, 대기 중일 때만. 취소한 신청은 남지 않는다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "취소 성공(CANCELED)"),
            @ApiResponse(responseCode = "404", description = "B001 없거나(이미 수락·취소됨 포함) 내가 보낸 신청이 아님"),
            @ApiResponse(responseCode = "409", description = "B004 이미 거절된 신청")
    })
    @DeleteMapping("/{requestId}")
    ResponseEntity<SuccessResponse<BuddyRequestResponse>> cancel(@LoginUserId Long userId, @PathVariable Long requestId);

    @Operation(summary = "버디 신청 수락", description = "받은 사람만. 수락하면 서로 버디가 되고 서로의 모임 활동을 볼 수 있다. 수락한 신청은 남지 않는다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수락 성공(ACCEPTED)"),
            @ApiResponse(responseCode = "404", description = "B001 없거나(이미 수락·취소됨 포함) 내가 받은 신청이 아님"),
            @ApiResponse(responseCode = "409", description = "B004 이미 거절된 신청")
    })
    @PostMapping("/{requestId}/accept")
    ResponseEntity<SuccessResponse<BuddyRequestResponse>> accept(@LoginUserId Long userId, @PathVariable Long requestId);

    @Operation(summary = "버디 신청 거절", description = "받은 사람만. 거절된 사람은 다시 신청할 수 있다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "거절 성공(REJECTED)"),
            @ApiResponse(responseCode = "404", description = "B001 없거나(이미 수락·취소됨 포함) 내가 받은 신청이 아님"),
            @ApiResponse(responseCode = "409", description = "B004 이미 거절된 신청")
    })
    @PostMapping("/{requestId}/reject")
    ResponseEntity<SuccessResponse<BuddyRequestResponse>> reject(@LoginUserId Long userId, @PathVariable Long requestId);
}
