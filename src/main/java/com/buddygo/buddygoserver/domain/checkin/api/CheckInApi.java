package com.buddygo.buddygoserver.domain.checkin.api;

import com.buddygo.buddygoserver.domain.checkin.dto.request.CheckInRequest;
import com.buddygo.buddygoserver.domain.checkin.dto.response.CheckInResponse;
import com.buddygo.buddygoserver.domain.checkin.dto.response.CheckInStatusResponse;
import com.buddygo.buddygoserver.domain.checkin.exception.CheckInOutOfRangeResponse;
import com.buddygo.buddygoserver.global.response.success.SuccessResponse;
import com.buddygo.buddygoserver.global.security.LoginUserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@Tag(name = "check-in", description = "GPS 체크인 · 체크인 현황")
@ApiResponses(@ApiResponse(responseCode = "401", description = "G004 토큰 없음 · A002 유효하지 않음 · A003 만료"))
@RequestMapping("/api/gatherings/{gatheringId}")
public interface CheckInApi {

    @Operation(summary = "체크인",
            description = """
                    호스트를 포함한 확정 참가자가 기기 GPS 좌표로 체크인한다. 마감(정원이 찬) 모임에서 시작 10분 전부터 시작 후 10분 전까지 열리고,
                    시작 후 5분 전까지는 정상(ON_TIME), 그 뒤는 지각(LATE)이다. 모임 장소에서 50m 안이어야 하며 범위 밖이면 현재 거리를 준다.
                    한 번만 할 수 있고 고칠 수 없다. 지각이면 같은 요청에서 매너온도가 내려간다(노쇼보다 작게).
                    시작 후 10분까지 체크인하지 않으면 서버가 노쇼로 기록하고 매너온도를 내린다.""")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "체크인 성공"),
            @ApiResponse(responseCode = "400", description = "G002 좌표 누락·범위 밖 값"),
            @ApiResponse(responseCode = "403", description = "C001 확정된 참가자가 아님"),
            @ApiResponse(responseCode = "404", description = "GA001 없거나 삭제된 모임"),
            @ApiResponse(responseCode = "409", description = "C002 이미 체크인함 또는 노쇼로 기록됨(문구로 구분) · C004 체크인 시간이 아님(열리기 전·지남·마감되지 않음·취소됨)"),
            @ApiResponse(responseCode = "422", description = "C003 범위 밖. 현재 거리(distanceMeters)와 반경(radiusMeters)을 함께 준다",
                    content = @Content(schema = @Schema(implementation = CheckInOutOfRangeResponse.class)))
    })
    @PostMapping("/check-in")
    ResponseEntity<SuccessResponse<CheckInResponse>> checkIn(
            @LoginUserId Long userId, @PathVariable Long gatheringId, @Valid @RequestBody CheckInRequest request);

    @Operation(summary = "체크인 현황",
            description = """
                    호스트와 확정 참가자만. 체크인 창(열림·지각 시작·마감 시각)과 반경, 내 체크인 결과를 준다.
                    확정 명단별 결과(members)는 모임 상세의 명단 공개 규칙을 따른다: 호스트는 항상, 확정 참가자는 마감 이후에만 받는다.
                    다른 사람의 체크인 거리는 주지 않는다.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "403", description = "C001 확정된 참가자가 아님"),
            @ApiResponse(responseCode = "404", description = "GA001 없거나 삭제된 모임")
    })
    @GetMapping("/check-ins")
    ResponseEntity<SuccessResponse<CheckInStatusResponse>> getStatus(
            @LoginUserId Long userId, @PathVariable Long gatheringId);
}
