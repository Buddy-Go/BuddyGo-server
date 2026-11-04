package com.buddygo.buddygoserver.domain.gathering.api;

import com.buddygo.buddygoserver.domain.gathering.dto.request.GatheringCreateRequest;
import com.buddygo.buddygoserver.domain.gathering.dto.response.GatheringDetailResponse;
import com.buddygo.buddygoserver.global.response.success.SuccessResponse;
import com.buddygo.buddygoserver.global.security.LoginUserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
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
}
