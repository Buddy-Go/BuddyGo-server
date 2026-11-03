package com.buddygo.buddygoserver.global.security.jwt;

import com.buddygo.buddygoserver.domain.auth.exception.AuthErrorType;
import com.buddygo.buddygoserver.global.exception.CustomException;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 토큰 발급과 리프레시 토큰 검증을 한곳에 모은다. 액세스 토큰 검증은 Resource Server 필터가 맡는다. */
@Component
public class JwtTokenProvider {

    public static final String FAMILY_CLAIM = "fid";

    private final JwtEncoder encoder;
    private final JwtDecoder refreshTokenDecoder;
    private final JwtProperties properties;

    public JwtTokenProvider(JwtEncoder encoder, SecretKey jwtSecretKey, JwtProperties properties) {
        this.encoder = encoder;
        this.refreshTokenDecoder = JwtConfig.createDecoder(jwtSecretKey, properties, TokenType.REFRESH);
        this.properties = properties;
    }

    public IssuedToken issueAccessToken(Long userId) {
        return issue(userId, TokenType.ACCESS, properties.accessTokenTtl().toSeconds(), null);
    }

    /** @param familyId 로그인 한 번(기기 하나)마다 생기는 세션 식별자. 회전해도 유지된다. */
    public IssuedToken issueRefreshToken(Long userId, String familyId) {
        return issue(userId, TokenType.REFRESH, properties.refreshTokenTtl().toSeconds(), familyId);
    }

    public RefreshTokenClaims parseRefreshToken(String token) {
        try {
            Jwt jwt = refreshTokenDecoder.decode(token);
            String familyId = jwt.getClaimAsString(FAMILY_CLAIM);
            if (familyId == null) {
                throw new CustomException(AuthErrorType.INVALID_TOKEN);
            }
            return new RefreshTokenClaims(Long.valueOf(jwt.getSubject()), familyId);
        } catch (JwtException e) {
            throw new CustomException(JwtExpiration.isExpired(e) ? AuthErrorType.EXPIRED_TOKEN : AuthErrorType.INVALID_TOKEN);
        }
    }

    private IssuedToken issue(Long userId, TokenType type, long ttlSeconds, String familyId) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(ttlSeconds);
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .audience(List.of(properties.audience()))
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiresAt(expiresAt)
                .id(UUID.randomUUID().toString())
                .claim(TokenType.CLAIM, type.name());
        if (familyId != null) {
            claims.claim(FAMILY_CLAIM, familyId);
        }
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String value = encoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
        return new IssuedToken(value, ttlSeconds);
    }

    public record IssuedToken(String value, long expiresInSeconds) {
    }

    public record RefreshTokenClaims(Long userId, String familyId) {
    }
}
