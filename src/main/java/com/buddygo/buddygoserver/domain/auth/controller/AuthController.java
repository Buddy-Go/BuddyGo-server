package com.buddygo.buddygoserver.domain.auth.controller;

import com.buddygo.buddygoserver.domain.auth.api.AuthApi;
import com.buddygo.buddygoserver.domain.auth.dto.request.LoginRequest;
import com.buddygo.buddygoserver.domain.auth.dto.request.RefreshTokenRequest;
import com.buddygo.buddygoserver.domain.auth.dto.request.SignupRequest;
import com.buddygo.buddygoserver.domain.auth.dto.response.AvailabilityResponse;
import com.buddygo.buddygoserver.domain.auth.dto.response.TokenResponse;
import com.buddygo.buddygoserver.domain.auth.service.AuthCommandService;
import com.buddygo.buddygoserver.domain.auth.service.AuthQueryService;
import com.buddygo.buddygoserver.global.response.success.SuccessResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AuthController implements AuthApi {

    private final AuthCommandService authCommandService;
    private final AuthQueryService authQueryService;

    @Override
    public ResponseEntity<SuccessResponse<TokenResponse>> signup(SignupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(SuccessResponse.created(authCommandService.signup(request)));
    }

    @Override
    public ResponseEntity<SuccessResponse<TokenResponse>> login(LoginRequest request) {
        return ResponseEntity.ok(SuccessResponse.from(authCommandService.login(request)));
    }

    @Override
    public ResponseEntity<SuccessResponse<TokenResponse>> refresh(RefreshTokenRequest request) {
        return ResponseEntity.ok(SuccessResponse.from(authCommandService.refresh(request.refreshToken())));
    }

    @Override
    public ResponseEntity<SuccessResponse<Void>> logout(RefreshTokenRequest request) {
        authCommandService.logout(request.refreshToken());
        return ResponseEntity.ok(SuccessResponse.ok());
    }

    @Override
    public ResponseEntity<SuccessResponse<AvailabilityResponse>> checkEmail(String email) {
        return ResponseEntity.ok(SuccessResponse.from(authQueryService.checkEmail(email)));
    }

    @Override
    public ResponseEntity<SuccessResponse<AvailabilityResponse>> checkNickname(String nickname) {
        return ResponseEntity.ok(SuccessResponse.from(authQueryService.checkNickname(nickname)));
    }
}
