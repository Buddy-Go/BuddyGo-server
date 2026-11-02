package com.buddygo.buddygoserver.domain.auth.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;

/**
 * 이메일별 로그인 실패 횟수 (Redis 고정 윈도우).
 * auth:login-fail:{email}  String(count)  TTL = 윈도우. 첫 실패 시각부터 윈도우가 끝나면 자동으로 풀린다.
 */
@Repository
@RequiredArgsConstructor
public class LoginAttemptRepository {

    private static final String KEY = "auth:login-fail:";

    private final StringRedisTemplate redisTemplate;

    public long getFailureCount(String email) {
        String value = redisTemplate.opsForValue().get(KEY + email);
        return value == null ? 0 : Long.parseLong(value);
    }

    public void increaseFailure(String email, Duration window) {
        Long count = redisTemplate.opsForValue().increment(KEY + email);
        if (count != null && count == 1) {
            redisTemplate.expire(KEY + email, window);
        }
    }

    public void reset(String email) {
        redisTemplate.delete(KEY + email);
    }
}
