package com.buddygo.buddygoserver.domain.application.api;

import com.buddygo.buddygoserver.domain.application.dto.request.ApplyRequest;
import com.buddygo.buddygoserver.domain.application.dto.response.ApplicantsResponse;
import com.buddygo.buddygoserver.domain.application.dto.response.ApplicationResponse;
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

@Tag(name = "application", description = "참여 신청 · 수락 · 거절 · 나가기")
@ApiResponses(@ApiResponse(responseCode = "401", description = "G004 토큰 없음 · A002 유효하지 않음 · A003 만료"))
@RequestMapping("/api/gatherings/{gatheringId}")
public interface ApplicationApi {

    @Operation(summary = "참여 신청",
            description = """
                    모집중이고 데드라인 전인 모임에 신청한다. 한마디(200자)와 헬스 수준은 선택이고 본문 없이 보내도 된다.
                    헬스 수준은 신청에 저장돼 호스트에게 보이고, 보낸 부위는 내 프로필에도 반영된다. 생략하면 지금 프로필 기록을 쓴다.
                    기준이 맞지 않거나 다른 모임과 시간이 겹쳐도 막지 않는다. 취소·거절·나간 뒤에도 다시 신청할 수 있다.""")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "신청 성공(PENDING)"),
            @ApiResponse(responseCode = "400", description = "G002 한마디 200자 초과·부위에 맞지 않는 기록·같은 부위 중복 · AP009 모임 부위가 아님"),
            @ApiResponse(responseCode = "403", description = "AP004 성별 조건 불일치"),
            @ApiResponse(responseCode = "404", description = "GA001 없거나 삭제된 모임"),
            @ApiResponse(responseCode = "409", description = "GA003 모집중이 아님(데드라인 지남 포함) · AP002 이미 대기·확정 · AP006 본인이 호스트")
    })
    @PostMapping("/applications")
    ResponseEntity<SuccessResponse<ApplicationResponse>> apply(
            @LoginUserId Long userId, @PathVariable Long gatheringId,
            @Valid @RequestBody(required = false) ApplyRequest request);

    @Operation(summary = "참여 신청 취소", description = "대기 중인 내 신청을 취소한다. 신청 기록은 남지 않고 매너온도 영향도 없으며, 모집중이면 다시 신청할 수 있다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "취소 성공"),
            @ApiResponse(responseCode = "404", description = "GA001 없거나 삭제된 모임 · AP001 대기·확정 중인 신청 없음"),
            @ApiResponse(responseCode = "409", description = "AP005 이미 확정됨(나가기를 사용)")
    })
    @DeleteMapping("/applications/me")
    ResponseEntity<SuccessResponse<Void>> cancelMine(@LoginUserId Long userId, @PathVariable Long gatheringId);

    @Operation(summary = "참가자 관리(신청자 목록)",
            description = """
                    호스트만. 확정 명단(호스트 포함)과 대기 중인 신청을 함께 준다.
                    신청자마다 닉네임·매너온도·한마디와 신청 시점의 헬스 수준(모임 부위, 없으면 recorded=false '미설정')을 담는다.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "403", description = "GA002 호스트가 아님"),
            @ApiResponse(responseCode = "404", description = "GA001 없거나 삭제된 모임")
    })
    @GetMapping("/applications")
    ResponseEntity<SuccessResponse<ApplicantsResponse>> getApplicants(
            @LoginUserId Long userId, @PathVariable Long gatheringId);

    @Operation(summary = "신청 수락",
            description = "호스트만. 수락하면 즉시 확정 인원에 반영되고, 정원이 차면 모임이 마감(CLOSED)되며 남은 대기 신청은 자동 거절된다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수락 성공. gatheringStatus 로 마감 여부를 알 수 있다."),
            @ApiResponse(responseCode = "403", description = "GA002 호스트가 아님"),
            @ApiResponse(responseCode = "404", description = "GA001 없거나 삭제된 모임 · AP001 이 모임의 신청이 아님"),
            @ApiResponse(responseCode = "409", description = "AP005 대기 중이 아님 · GA003 모집중이 아님(데드라인 지남 포함) · AP003 정원 참")
    })
    @PostMapping("/applications/{applicationId}/accept")
    ResponseEntity<SuccessResponse<ApplicationResponse>> accept(
            @LoginUserId Long userId, @PathVariable Long gatheringId, @PathVariable Long applicationId);

    @Operation(summary = "신청 거절", description = "호스트만. 대기 중인 신청을 거절한다. 거절된 사람도 모집중이면 다시 신청할 수 있다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "거절 성공"),
            @ApiResponse(responseCode = "403", description = "GA002 호스트가 아님"),
            @ApiResponse(responseCode = "404", description = "GA001 없거나 삭제된 모임 · AP001 이 모임의 신청이 아님"),
            @ApiResponse(responseCode = "409", description = "AP005 대기 중이 아님")
    })
    @PostMapping("/applications/{applicationId}/reject")
    ResponseEntity<SuccessResponse<ApplicationResponse>> reject(
            @LoginUserId Long userId, @PathVariable Long gatheringId, @PathVariable Long applicationId);

    @Operation(summary = "모임 나가기",
            description = """
                    확정 참가자만. 호스트는 나갈 수 없다(모임 취소를 사용). 진행중이 된 뒤에는 나갈 수 없다.
                    - 모집중: 확정 인원만 줄어든다. 매너온도 영향 없음
                    - 마감 · 데드라인 전: 마감이 풀려 다시 모집한다(gatheringStatus=RECRUITING). 나간 사람 매너온도 하락
                    - 마감 · 데드라인 후: 모임 전체가 취소된다(gatheringStatus=CANCELED). 나간 사람 매너온도가 데드라인 전보다 크게 하락.
                      확인 창에 '지금 나가면 모임이 취소돼요'를 안내한다""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "나가기 성공(status=LEFT). gatheringStatus 로 모임에 생긴 일을 알 수 있다."),
            @ApiResponse(responseCode = "403", description = "AP007 확정 참가자가 아님"),
            @ApiResponse(responseCode = "404", description = "GA001 없거나 삭제된 모임"),
            @ApiResponse(responseCode = "409", description = "AP008 호스트 · GA008 이미 시작 · GA009 이미 취소")
    })
    @PostMapping("/leave")
    ResponseEntity<SuccessResponse<ApplicationResponse>> leave(@LoginUserId Long userId, @PathVariable Long gatheringId);
}
