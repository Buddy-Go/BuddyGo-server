package com.buddygo.buddygoserver.support;

import com.buddygo.buddygoserver.global.security.LoginUserId;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 보호된 API 동작과 @LoginUserId 주입을 검증하기 위한 테스트 전용 엔드포인트 */
@RestController
public class AuthTestController {

    @GetMapping("/api/test/me")
    public Map<String, Long> me(@LoginUserId Long userId) {
        return Map.of("userId", userId);
    }
}
