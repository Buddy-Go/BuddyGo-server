package com.buddygo.buddygoserver.global.security.jwt;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

@ConfigurationProperties("jwt")
public record JwtProperties(
        String secret,
        String issuer,
        String audience,
        Duration accessTokenTtl,
        Duration refreshTokenTtl
) {

    private static final int MIN_SECRET_BYTES = 32;

    /** 비밀키가 없거나 짧으면 기동 단계에서 실패시킨다. */
    public JwtProperties {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("jwt.secret(JWT_SECRET) 는 " + MIN_SECRET_BYTES + "바이트 이상이어야 합니다.");
        }
        if (issuer == null || audience == null || accessTokenTtl == null || refreshTokenTtl == null) {
            throw new IllegalStateException("jwt.issuer, audience, access-token-ttl, refresh-token-ttl 설정이 필요합니다.");
        }
    }
}
