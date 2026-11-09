package com.buddygo.buddygoserver.domain.profile.api;

import com.buddygo.buddygoserver.domain.profile.dto.response.MyProfileResponse;
import com.buddygo.buddygoserver.domain.profile.dto.response.UserProfileResponse;
import com.buddygo.buddygoserver.global.response.success.SuccessResponse;
import com.buddygo.buddygoserver.global.security.LoginUserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Tag(name = "user", description = "내 정보 · 운동 프로필 · 알림 설정")
@ApiResponses(@ApiResponse(responseCode = "401", description = "G004 토큰 없음 · A002 유효하지 않음 · A003 만료"))
public interface ProfileApi {

    @Operation(summary = "내 정보 조회", description = """
            마이페이지 기본 정보와 활동 요약(stats).
            후기 n은 아쉬움 평가까지 센 받은 평가 수, 개설 n회는 내가 연 모임 중 진행중·종료가 된 수,
            참여 n회는 남이 연 모임에서 체크인(정상·지각)한 수, 많이 받은 태그는 아쉬움 평가의 태그를 뺀 상위 3개.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "U001 없는 사용자")
    })
    @GetMapping("/api/users/me")
    ResponseEntity<SuccessResponse<MyProfileResponse>> getMe(@LoginUserId Long userId);

    @Operation(summary = "다른 사용자 프로필 조회", description = """
            닉네임·매너온도, 활동 요약(stats, 내 정보와 같은 기준), 나와의 버디 관계(buddy).
            버디 신청 버튼(canRequest)은 아무 관계가 없고 함께 운동한 적이 있을 때만 true.
            상대가 보낸 신청이 대기 중이면 REQUEST_RECEIVED 와 그 requestId 로 수락·거절한다.
            내 ID로 열면 me=true, 관계 NONE, canRequest=false.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "U001 없는 사용자")
    })
    @GetMapping("/api/users/{userId}")
    ResponseEntity<SuccessResponse<UserProfileResponse>> getUser(@LoginUserId Long viewerId, @PathVariable Long userId);
}
