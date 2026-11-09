package com.buddygo.buddygoserver.domain.user.api;

import com.buddygo.buddygoserver.domain.user.dto.request.FcmTokenRequest;
import com.buddygo.buddygoserver.domain.user.dto.request.NotificationSettingsRequest;
import com.buddygo.buddygoserver.domain.user.dto.response.MyActivitiesResponse;
import com.buddygo.buddygoserver.domain.user.dto.response.NotificationSettingsResponse;
import com.buddygo.buddygoserver.global.response.success.SuccessResponse;
import com.buddygo.buddygoserver.global.security.LoginUserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Tag(name = "user", description = "내 정보 · 운동 프로필 · 알림 설정")
@ApiResponses(@ApiResponse(responseCode = "401", description = "G004 토큰 없음 · A002 유효하지 않음 · A003 만료"))
@RequestMapping("/api/users/me")
public interface UserApi {

    @Operation(summary = "내 활동 내역", description = """
            role=host(내가 개설) 또는 participant(다른 사람 모임에 대기·확정). 대기·확정 신청만 보여주고
            거절·신청 취소·나가기와 삭제된 모임은 빠진다.
            period=upcoming: 모집중·마감·진행중 모임, 시작 시각 오름차순.
            period=past: 종료·취소된 모임, 시작 시각 내림차순.
            같은 시각이면 참여 ID 오름차순. 출결 기록이 없으면 결과를 임의로 판정하지 않는다.
            sport는 생략하거나 GYM(gym). 현재 헬스만 지원한다. page는 0부터, size는 1~100(기본 20).""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공. 내역이 없으면 content는 빈 배열"),
            @ApiResponse(responseCode = "400", description = "G002 잘못된 필터·페이지"),
            @ApiResponse(responseCode = "404", description = "U001 없는 사용자")
    })
    @GetMapping("/gatherings")
    ResponseEntity<SuccessResponse<MyActivitiesResponse>> getActivities(
            @LoginUserId Long userId,
            @RequestParam @Pattern(regexp = "host|participant") String role,
            @RequestParam(defaultValue = "upcoming") @Pattern(regexp = "upcoming|past") String period,
            @RequestParam(required = false) @Pattern(regexp = "(?i)gym") String sport,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size);

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
