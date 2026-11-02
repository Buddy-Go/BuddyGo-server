package com.buddygo.buddygoserver.domain.auth.api;

import com.buddygo.buddygoserver.domain.auth.dto.request.LoginRequest;
import com.buddygo.buddygoserver.domain.auth.dto.request.RefreshTokenRequest;
import com.buddygo.buddygoserver.domain.auth.dto.request.SignupRequest;
import com.buddygo.buddygoserver.domain.auth.dto.response.AvailabilityResponse;
import com.buddygo.buddygoserver.domain.auth.dto.response.TokenResponse;
import com.buddygo.buddygoserver.global.response.success.SuccessResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Tag(name = "auth", description = "회원가입 · 로그인 · 토큰 재발급 · 로그아웃")
@SecurityRequirements // 인증 없이 호출하는 API 라 Swagger 자물쇠를 뺀다
@RequestMapping("/api/auth")
public interface AuthApi {

    @Operation(summary = "이메일 회원가입", description = "가입과 동시에 액세스·리프레시 토큰을 발급한다.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "가입 성공"),
            @ApiResponse(responseCode = "400", description = "G002 입력값 오류"),
            @ApiResponse(responseCode = "409", description = "U002 이메일 중복 · U003 닉네임 중복")
    })
    @PostMapping("/signup")
    ResponseEntity<SuccessResponse<TokenResponse>> signup(@Valid @RequestBody SignupRequest request);

    @Operation(summary = "이메일 로그인")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "로그인 성공"),
            @ApiResponse(responseCode = "401", description = "A001 이메일 또는 비밀번호 불일치"),
            @ApiResponse(responseCode = "429", description = "A005 로그인 실패 횟수 초과")
    })
    @PostMapping("/login")
    ResponseEntity<SuccessResponse<TokenResponse>> login(@Valid @RequestBody LoginRequest request);

    @Operation(summary = "토큰 재발급",
            description = "리프레시 토큰을 새 토큰 쌍으로 교환한다. 보낸 리프레시 토큰은 더 이상 쓸 수 없다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "재발급 성공"),
            @ApiResponse(responseCode = "401",
                    description = "A002 유효하지 않음 · A003 만료 · A004 재사용 탐지(해당 기기 세션 폐기, 재로그인 필요)")
    })
    @PostMapping("/refresh")
    ResponseEntity<SuccessResponse<TokenResponse>> refresh(@Valid @RequestBody RefreshTokenRequest request);

    @Operation(summary = "로그아웃", description = "현재 기기의 세션(리프레시 토큰)을 폐기한다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "로그아웃 성공 (이미 끝난 세션이어도 200)"),
            @ApiResponse(responseCode = "401", description = "A002 유효하지 않은 토큰 · A003 만료")
    })
    @PostMapping("/logout")
    ResponseEntity<SuccessResponse<Void>> logout(@Valid @RequestBody RefreshTokenRequest request);

    @Operation(summary = "이메일 중복 확인")
    @GetMapping("/check-email")
    ResponseEntity<SuccessResponse<AvailabilityResponse>> checkEmail(
            @Parameter(example = "buddy@example.com")
            @RequestParam @NotBlank @Email(message = "이메일 형식이 올바르지 않습니다.") String email);

    @Operation(summary = "닉네임 중복 확인")
    @GetMapping("/check-nickname")
    ResponseEntity<SuccessResponse<AvailabilityResponse>> checkNickname(
            @Parameter(example = "헬스버디") @RequestParam @NotBlank String nickname);
}
