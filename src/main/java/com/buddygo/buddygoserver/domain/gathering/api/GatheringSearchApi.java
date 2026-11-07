package com.buddygo.buddygoserver.domain.gathering.api;

import com.buddygo.buddygoserver.domain.gathering.dto.request.GatheringSearchRequest;
import com.buddygo.buddygoserver.domain.gathering.dto.response.GatheringPinsResponse;
import com.buddygo.buddygoserver.domain.gathering.dto.response.GatheringSearchResponse;
import com.buddygo.buddygoserver.global.response.success.SuccessResponse;
import com.buddygo.buddygoserver.global.security.LoginUserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Tag(name = "explore", description = "탐색: 모임 리스트 · 지도 핀")
@ApiResponses(@ApiResponse(responseCode = "401", description = "G004 토큰 없음 · A002 유효하지 않음 · A003 만료"))
@RequestMapping("/api/gatherings")
public interface GatheringSearchApi {

    String CONDITIONS = """
            지도에는 모집중이고 데드라인 전인 모임만 나온다. 보내지 않은 조건은 '전체'다.
            - 검색 영역: 지도 범위(swLat·swLng·neLat·neLng 네 값) 또는 구·동(regionCode) 중 하나. 위치 조건이 있으면 앱이 regionCode 를, 없으면 지도 범위를 보낸다. 묶인 핀을 눌렀을 때는 parcelKey 만 보내도 된다
            - 날짜·시간: date(KST) 하나 + timeSlots(복수) 또는 fromTime·toTime. 시간 조건은 date 가 있어야 하고 두 방식을 함께 쓸 수 없다
            - 부위(parts): 고른 부위를 모두 포함하는 모임(AND)
            - 기준 범위(chestMin~armLevel): 범위를 보낸 부위 중 하나라도 모임 기준(호스트 기록)이 범위 안이면 포함(OR). 호스트가 기록을 숨긴 부위는 범위 밖으로 본다. 범위를 보낸 부위는 parts 에도 있어야 한다. 초깃값은 GET /api/users/me/profile 의 searchRanges
            - gender: 모임 성별 조건과 같은 것만 · minManner: 호스트 매너온도 이상 · likedOnly: 찜한 모임만""";

    @Operation(summary = "모임 리스트 검색", description = """
            탐색 하단 카드 리스트(무한 스크롤). lat·lng(내 위치)를 보내면 거리순이고 카드에 거리가 담긴다. 없으면 시작 시각순이고 거리는 null.
            - 첫 요청은 cursor 없이 보내고, 바닥에 닿으면 응답의 nextCursor 를 cursor 로 보낸다. nextCursor 가 null 이면 끝이다
            - 이어 받는 동안 조건과 lat·lng 는 첫 요청과 같게 보낸다. 조건을 바꾸면 cursor 없이 처음부터 받는다
            - 묶인 핀을 누르면 지금 걸린 조건(영역 제외 가능)에 parcelKey 를 더해 그 지번의 모임만 받는다. 지도·하단 리스트는 그대로 둔다
            """ + CONDITIONS)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "400", description = "G002 입력값 · GA016 검색 영역 · GA017 조건 조합(시간 조건에 날짜 없음, 범위 부위가 parts 에 없음, 하한 > 상한, lat·lng 중 하나만 등) · GA018 커서(형식 오류, 거리순 여부가 첫 요청과 다름)")
    })
    @GetMapping
    ResponseEntity<SuccessResponse<GatheringSearchResponse>> search(
            @LoginUserId Long userId,
            @Valid @ParameterObject @ModelAttribute GatheringSearchRequest request,
            @Parameter(description = "이전 응답의 nextCursor. 첫 요청은 비운다") @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size);

    @Operation(summary = "지도 핀 조회", description = """
            지번 주소가 같은 모임을 핀 하나로 묶고 모임 수를 준다. 조건은 리스트 검색과 같다(lat·lng 는 쓰지 않는다).
            """ + CONDITIONS)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "400", description = "G002 입력값 · GA016 검색 영역 · GA017 조건 조합")
    })
    @GetMapping("/pins")
    ResponseEntity<SuccessResponse<GatheringPinsResponse>> pins(
            @LoginUserId Long userId, @Valid @ParameterObject @ModelAttribute GatheringSearchRequest request);
}
