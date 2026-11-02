package com.buddygo.buddygoserver.domain.auth.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 리프레시 토큰 세션 저장소 (Redis).
 *
 * <pre>
 * auth:rt:family:{familyId}  Hash { userId, tokenHash }  TTL = 리프레시 토큰 수명
 *     로그인 한 번(기기 하나)이 하나의 패밀리다. 재발급할 때마다 tokenHash 만 새 토큰으로 바뀐다(회전).
 * auth:rt:user:{userId}      Set { familyId... }
 *     사용자의 모든 세션을 한 번에 끊을 때(탈퇴 등) 쓴다. 이미 만료된 familyId 가 남아 있어도 무해하다.
 * </pre>
 * 토큰 원문은 저장하지 않고 SHA-256 해시만 저장한다.
 */
@Repository
@RequiredArgsConstructor
public class RefreshTokenRepository {

    private static final String FAMILY_KEY = "auth:rt:family:";
    private static final String USER_KEY = "auth:rt:user:";

    /**
     * 비교 후 교체를 원자적으로 처리한다. 동시에 같은 토큰으로 두 번 재발급해도 한쪽만 성공한다.
     * 반환: 1 = 회전 성공, -1 = 재사용 탐지(패밀리 폐기), 0 = 세션 없음(만료·로그아웃) 또는 소유자 불일치
     */
    private static final RedisScript<Long> ROTATE_SCRIPT = RedisScript.of("""
            local current = redis.call('HGET', KEYS[1], 'tokenHash')
            if not current or redis.call('HGET', KEYS[1], 'userId') ~= ARGV[4] then
                return 0
            end
            if current == ARGV[1] then
                redis.call('HSET', KEYS[1], 'tokenHash', ARGV[2])
                redis.call('EXPIRE', KEYS[1], ARGV[3])
                redis.call('EXPIRE', KEYS[2], ARGV[3])
                return 1
            end
            redis.call('DEL', KEYS[1])
            redis.call('SREM', KEYS[2], ARGV[5])
            return -1
            """, Long.class);

    private final StringRedisTemplate redisTemplate;

    public void save(String familyId, Long userId, String refreshToken, Duration ttl) {
        String familyKey = FAMILY_KEY + familyId;
        String userKey = USER_KEY + userId;
        redisTemplate.opsForHash().putAll(familyKey, Map.of(
                "userId", String.valueOf(userId),
                "tokenHash", hash(refreshToken)));
        redisTemplate.expire(familyKey, ttl);
        redisTemplate.opsForSet().add(userKey, familyId);
        redisTemplate.expire(userKey, ttl);
    }

    public RotationResult rotate(String familyId, Long userId, String presentedToken, String newToken, Duration ttl) {
        Long result = redisTemplate.execute(ROTATE_SCRIPT,
                List.of(FAMILY_KEY + familyId, USER_KEY + userId),
                hash(presentedToken), hash(newToken), String.valueOf(ttl.toSeconds()),
                String.valueOf(userId), familyId);
        if (result == null || result == 0) {
            return RotationResult.NOT_FOUND;
        }
        return result == 1 ? RotationResult.ROTATED : RotationResult.REUSED;
    }

    /** 기기 하나 로그아웃. 다른 사용자의 패밀리는 지우지 않는다. */
    public void deleteFamily(String familyId, Long userId) {
        String familyKey = FAMILY_KEY + familyId;
        Object owner = redisTemplate.opsForHash().get(familyKey, "userId");
        if (String.valueOf(userId).equals(owner)) {
            redisTemplate.delete(familyKey);
            redisTemplate.opsForSet().remove(USER_KEY + userId, familyId);
        }
    }

    /** 모든 기기 로그아웃 (탈퇴, 계정 이상 등) */
    public void deleteAllByUser(Long userId) {
        String userKey = USER_KEY + userId;
        Set<String> familyIds = redisTemplate.opsForSet().members(userKey);
        if (familyIds != null && !familyIds.isEmpty()) {
            redisTemplate.delete(familyIds.stream().map(id -> FAMILY_KEY + id).toList());
        }
        redisTemplate.delete(userKey);
    }

    private static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public enum RotationResult {
        ROTATED,
        REUSED,
        NOT_FOUND
    }
}
