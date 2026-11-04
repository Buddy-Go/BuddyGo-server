package com.buddygo.buddygoserver.domain.sport.gym.api;

import com.buddygo.buddygoserver.domain.sport.gym.dto.request.GymRecordsRequest;
import com.buddygo.buddygoserver.domain.sport.gym.dto.response.GymProfileResponse;
import com.buddygo.buddygoserver.global.response.success.SuccessResponse;
import com.buddygo.buddygoserver.global.security.LoginUserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@Tag(name = "user", description = "내 정보 · 운동 프로필 · 알림 설정")
@RequestMapping("/api/users/me")
public interface GymRecordApi {

    @Operation(summary = "운동 프로필 조회", description = "헬스 부위별 기록. 다섯 부위를 항상 담고, 미설정 부위는 recorded=false 다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", description = "G004 토큰 없음 · A002 유효하지 않음 · A003 만료")
    })
    @GetMapping("/profile")
    ResponseEntity<SuccessResponse<GymProfileResponse>> getMyProfile(@LoginUserId Long userId);

    @Operation(summary = "헬스 기록 저장",
            description = """
                    보낸 부위가 내 기록 전체가 된다. 빠진 부위는 미설정으로 지워지고, 빈 배열이면 모두 지운다.
                    - CHEST·LEGS·SHOULDER: recordValue = 1RM(kg, 정수, 1~300)
                    - BACK: recordValue = 맨몸 턱걸이 연속 최대 횟수(정수, 0~100)
                    - ARM: armLevel 만(BEGINNER / INTERMEDIATE / ADVANCED)""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "저장 성공. 저장 후의 운동 프로필을 돌려준다."),
            @ApiResponse(responseCode = "400", description = "G002 부위에 맞지 않는 값 · 범위 밖 · 같은 부위 중복"),
            @ApiResponse(responseCode = "401", description = "G004 토큰 없음 · A002 유효하지 않음 · A003 만료")
    })
    @PutMapping("/gym-records")
    ResponseEntity<SuccessResponse<GymProfileResponse>> replaceMyGymRecords(
            @LoginUserId Long userId, @Valid @RequestBody GymRecordsRequest request);
}
