package com.buddygo.buddygoserver.domain.application.api;

import com.buddygo.buddygoserver.domain.application.dto.request.ApplyRequest;
import com.buddygo.buddygoserver.domain.application.dto.response.ApplicationResponse;
import com.buddygo.buddygoserver.global.response.success.SuccessResponse;
import com.buddygo.buddygoserver.global.security.LoginUserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@Tag(name = "application", description = "참여 신청 · 수락 · 거절 · 나가기")
@ApiResponses(@ApiResponse(responseCode = "401", description = "G004 토큰 없음 · A002 유효하지 않음 · A003 만료"))
@RequestMapping("/api/gatherings/{gatheringId}")
public interface ApplicationApi {

    @Operation(summary = "참여 신청",
            description = """
                    모집중이고 데드라인 전인 모임에 신청한다. 한마디는 선택(200자)이고 본문 없이 보내도 된다.
                    기준이 맞지 않거나 다른 모임과 시간이 겹쳐도 막지 않는다. 취소·거절·나간 뒤에도 다시 신청할 수 있다.""")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "신청 성공(PENDING)"),
            @ApiResponse(responseCode = "400", description = "G002 한마디 200자 초과"),
            @ApiResponse(responseCode = "403", description = "AP004 성별 조건 불일치"),
            @ApiResponse(responseCode = "404", description = "GA001 없거나 삭제된 모임"),
            @ApiResponse(responseCode = "409", description = "GA003 모집중이 아님(데드라인 지남 포함) · AP002 이미 대기·확정 · AP006 본인이 호스트")
    })
    @PostMapping("/applications")
    ResponseEntity<SuccessResponse<ApplicationResponse>> apply(
            @LoginUserId Long userId, @PathVariable Long gatheringId,
            @Valid @RequestBody(required = false) ApplyRequest request);
}
