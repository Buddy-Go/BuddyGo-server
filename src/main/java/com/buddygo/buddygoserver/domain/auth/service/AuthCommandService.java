package com.buddygo.buddygoserver.domain.auth.service;

import com.buddygo.buddygoserver.domain.auth.dto.request.LoginRequest;
import com.buddygo.buddygoserver.domain.auth.dto.request.SignupRequest;
import com.buddygo.buddygoserver.domain.auth.dto.response.TokenResponse;
import com.buddygo.buddygoserver.domain.auth.exception.AuthErrorType;
import com.buddygo.buddygoserver.domain.auth.repository.RefreshTokenRepository;
import com.buddygo.buddygoserver.domain.auth.repository.RefreshTokenRepository.RotationResult;
import com.buddygo.buddygoserver.domain.user.entity.User;
import com.buddygo.buddygoserver.domain.user.exception.UserErrorType;
import com.buddygo.buddygoserver.domain.user.repository.UserRepository;
import com.buddygo.buddygoserver.global.exception.CustomException;
import com.buddygo.buddygoserver.global.security.jwt.JwtProperties;
import com.buddygo.buddygoserver.global.security.jwt.JwtTokenProvider;
import com.buddygo.buddygoserver.global.security.jwt.JwtTokenProvider.IssuedToken;
import com.buddygo.buddygoserver.global.security.jwt.JwtTokenProvider.RefreshTokenClaims;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
public class AuthCommandService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final LoginAttemptLimiter loginAttemptLimiter;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtProperties jwtProperties;
    private final PasswordEncoder passwordEncoder;
    /** 없는 이메일로 로그인할 때도 해시 비교를 한 번 해서 응답 시간으로 가입 여부가 드러나지 않게 한다. */
    private final String dummyPasswordHash;

    public AuthCommandService(UserRepository userRepository, RefreshTokenRepository refreshTokenRepository,
                              LoginAttemptLimiter loginAttemptLimiter, JwtTokenProvider jwtTokenProvider,
                              JwtProperties jwtProperties, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.loginAttemptLimiter = loginAttemptLimiter;
        this.jwtTokenProvider = jwtTokenProvider;
        this.jwtProperties = jwtProperties;
        this.passwordEncoder = passwordEncoder;
        this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    /** 이메일 회원가입. 가입과 동시에 로그인 상태가 된다. */
    @Transactional
    public TokenResponse signup(SignupRequest request) {
        String email = EmailNormalizer.normalize(request.email());
        String nickname = request.nickname().strip();
        validateDuplicate(email, nickname);

        User user = User.signUpWithEmail(email, passwordEncoder.encode(request.password()),
                request.name().strip(), nickname, request.gender());
        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // 중복 확인과 저장 사이에 같은 값으로 가입한 경우. 유니크 제약이 최종 방어선이다.
            validateDuplicate(email, nickname);
            throw e;
        }
        return startSession(user.getId());
    }

    public TokenResponse login(LoginRequest request) {
        String email = EmailNormalizer.normalize(request.email());
        loginAttemptLimiter.checkBlocked(email);

        User user = userRepository.findByEmail(email).orElse(null);
        String hash = (user != null && user.getPasswordHash() != null) ? user.getPasswordHash() : dummyPasswordHash;
        boolean matches = passwordEncoder.matches(request.password(), hash);
        if (user == null || user.getPasswordHash() == null || !matches) {
            loginAttemptLimiter.recordFailure(email);
            throw new CustomException(AuthErrorType.INVALID_CREDENTIALS);
        }
        loginAttemptLimiter.reset(email);
        return startSession(user.getId());
    }

    /**
     * 리프레시 토큰 회전. 쓸 때마다 새 토큰으로 바꾸고, 이미 바뀐(옛) 토큰이 다시 오면
     * 탈취로 보고 그 세션(패밀리)을 폐기한다.
     */
    public TokenResponse refresh(String refreshToken) {
        RefreshTokenClaims claims = jwtTokenProvider.parseRefreshToken(refreshToken);
        if (!userRepository.existsById(claims.userId())) {
            refreshTokenRepository.deleteAllByUser(claims.userId());
            throw new CustomException(AuthErrorType.INVALID_TOKEN);
        }

        IssuedToken newRefresh = jwtTokenProvider.issueRefreshToken(claims.userId(), claims.familyId());
        RotationResult result = refreshTokenRepository.rotate(claims.familyId(), claims.userId(),
                refreshToken, newRefresh.value(), jwtProperties.refreshTokenTtl());

        return switch (result) {
            case ROTATED -> {
                IssuedToken access = jwtTokenProvider.issueAccessToken(claims.userId());
                yield TokenResponse.of(access.value(), access.expiresInSeconds(),
                        newRefresh.value(), newRefresh.expiresInSeconds());
            }
            case REUSED -> {
                log.warn("[Security] 리프레시 토큰 재사용 탐지 userId={} familyId={}", claims.userId(), claims.familyId());
                throw new CustomException(AuthErrorType.REFRESH_TOKEN_REUSED);
            }
            case NOT_FOUND -> throw new CustomException(AuthErrorType.INVALID_TOKEN);
        };
    }

    /** 현재 기기 로그아웃. 이미 끝난 세션이어도 성공으로 처리한다(멱등). */
    public void logout(String refreshToken) {
        RefreshTokenClaims claims = jwtTokenProvider.parseRefreshToken(refreshToken);
        refreshTokenRepository.deleteFamily(claims.familyId(), claims.userId());
    }

    private TokenResponse startSession(Long userId) {
        String familyId = UUID.randomUUID().toString();
        IssuedToken access = jwtTokenProvider.issueAccessToken(userId);
        IssuedToken refresh = jwtTokenProvider.issueRefreshToken(userId, familyId);
        refreshTokenRepository.save(familyId, userId, refresh.value(), jwtProperties.refreshTokenTtl());
        return TokenResponse.of(access.value(), access.expiresInSeconds(), refresh.value(), refresh.expiresInSeconds());
    }

    private void validateDuplicate(String email, String nickname) {
        if (userRepository.existsByEmail(email)) {
            throw new CustomException(UserErrorType.DUPLICATE_EMAIL);
        }
        if (userRepository.existsByNickname(nickname)) {
            throw new CustomException(UserErrorType.DUPLICATE_NICKNAME);
        }
    }
}
