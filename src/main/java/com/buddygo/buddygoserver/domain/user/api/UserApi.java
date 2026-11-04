package com.buddygo.buddygoserver.domain.user.api;

import com.buddygo.buddygoserver.domain.user.dto.request.FcmTokenRequest;
import com.buddygo.buddygoserver.domain.user.dto.request.NotificationSettingsRequest;
import com.buddygo.buddygoserver.domain.user.dto.response.MyInfoResponse;
import com.buddygo.buddygoserver.domain.user.dto.response.NotificationSettingsResponse;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@Tag(name = "user", description = "내 정보 · 운동 프로필 · 알림 설정")
@ApiResponses(@ApiResponse(responseCode = "401", description = "G004 토큰 없음 · A002 유효하지 않음 · A003 만료"))
@RequestMapping("/api/users/me")
public interface UserApi {

    @Operation(summary = "내 정보 조회", description = "마이페이지 기본 정보. 후기 수·개설/참여 횟수·많이 받은 태그는 아직 없다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping
    ResponseEntity<SuccessResponse<MyInfoResponse>> getMe(@LoginUserId Long userId);

    @Operation(summary = "전체 알림 설정", description = "끄면 이 사용자에게 푸시를 보내지 않는다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "변경 성공"),
            @ApiResponse(responseCode = "400", description = "G002 pushEnabled 누락")
    })
    @PatchMapping("/notification-settings")
    ResponseEntity<SuccessResponse<NotificationSettingsResponse>> changeNotificationSettings(
            @LoginUserId Long userId, @Valid @RequestBody NotificationSettingsRequest request);

    @Operation(summary = "푸시 토큰 등록",
            description = "앱 실행·로그인·토큰 갱신 때 호출한다. 같은 토큰이 다른 계정에 있으면 이 계정으로 옮긴다. 기기마다 하나씩 쌓인다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "등록 성공"),
            @ApiResponse(responseCode = "400", description = "G002 토큰 누락")
    })
    @PutMapping("/fcm-token")
    ResponseEntity<SuccessResponse<Void>> registerFcmToken(
            @LoginUserId Long userId, @Valid @RequestBody FcmTokenRequest request);
}
