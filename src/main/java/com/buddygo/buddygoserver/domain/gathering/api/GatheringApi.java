package com.buddygo.buddygoserver.domain.gathering.api;

import com.buddygo.buddygoserver.domain.gathering.dto.request.GatheringCreateRequest;
import com.buddygo.buddygoserver.domain.gathering.dto.request.GatheringUpdateRequest;
import com.buddygo.buddygoserver.domain.gathering.dto.response.GatheringDetailResponse;
import com.buddygo.buddygoserver.global.response.success.SuccessResponse;
import com.buddygo.buddygoserver.global.security.LoginUserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@Tag(name = "gathering", description = "모임 개설 · 상세 · 수정 · 삭제 · 취소")
@ApiResponses(@ApiResponse(responseCode = "401", description = "G004 토큰 없음 · A002 유효하지 않음 · A003 만료"))
@RequestMapping("/api/gatherings")
public interface GatheringApi {

    @Operation(summary = "모임 개설",
            description = """
                    헬스 모임을 연다. 호스트는 확정 참가자로 정원에 포함된다.
                    - 시작 시각: 10분 단위 정각(초 0), 지금부터 1시간 30분 이후(데드라인이 미래여야 함)
                    - 진행 시간: 30~240분, 10분 단위. 종료 시각 = 시작 + 진행 시간
                    - 정원: 호스트 포함 2~3명
                    - 성별 조건: 호스트 성별과 다른 성별 제한은 고를 수 없다
                    - 장소: kakaoPlaceId 가 있으면 기존 장소를 재사용, 없으면(지도에서 찍은 집결지) 새로 만든다
                    - gym.showMyRecord 를 켜면 지금의 내 헬스 기록을 모임에 복사한다(기록 없는 부위는 표시 안 됨)""")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "개설 성공. 개설된 모임 상세를 돌려준다."),
            @ApiResponse(responseCode = "400", description = "G002 입력값 · GA010 시작 시각이 너무 이름 · GA011 정원 · GA012 진행 시간 · GA013 성별 조건 · GA014 시작 시각 단위")
    })
    @PostMapping
    ResponseEntity<SuccessResponse<GatheringDetailResponse>> create(
            @LoginUserId Long userId, @Valid @RequestBody GatheringCreateRequest request);

    @Operation(summary = "모임 상세",
            description = """
                    보는 사람에 따라 내용이 다르다(viewer.role: HOST / PARTICIPANT / APPLICANT / NONE).
                    - members: 호스트는 항상, 확정 참가자는 마감(CLOSED) 이후에만, 그 외에는 null
                    - pendingCount: 대기 중인 신청 수. 호스트에게만
                    - gym.match: 내 기록과 모임 기준의 일치 판정. 호스트 본인에게는 null
                    - cancellation.reason: 호스트·확정 참가자에게만""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "GA001 없거나 삭제된 모임")
    })
    @GetMapping("/{gatheringId}")
    ResponseEntity<SuccessResponse<GatheringDetailResponse>> getDetail(
            @LoginUserId Long userId, @PathVariable Long gatheringId);

    @Operation(summary = "모임 수정",
            description = """
                    보낸 항목만 바꾼다(null = 그대로). 설명·상세주소는 빈 문자열이면 지운다. 시작 전까지만 수정할 수 있다.
                    - 확정 참가자(호스트 제외) 0명: 모든 항목
                    - 1명 이상: 제목·설명·상세주소만. 다른 항목을 보내면 GA005""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수정 성공. 수정된 모임 상세를 돌려준다."),
            @ApiResponse(responseCode = "400", description = "G002 입력값 · GA010 · GA011 · GA012 · GA013 · GA014"),
            @ApiResponse(responseCode = "403", description = "GA002 호스트가 아님"),
            @ApiResponse(responseCode = "404", description = "GA001 없거나 삭제된 모임"),
            @ApiResponse(responseCode = "409", description = "GA005 잠긴 항목 수정 · GA008 이미 시작 · GA009 이미 취소")
    })
    @PatchMapping("/{gatheringId}")
    ResponseEntity<SuccessResponse<GatheringDetailResponse>> update(
            @LoginUserId Long userId, @PathVariable Long gatheringId, @Valid @RequestBody GatheringUpdateRequest request);
}
