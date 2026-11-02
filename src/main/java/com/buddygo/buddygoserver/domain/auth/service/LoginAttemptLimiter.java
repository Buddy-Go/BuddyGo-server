package com.buddygo.buddygoserver.domain.auth.service;

import com.buddygo.buddygoserver.domain.auth.exception.AuthErrorType;
import com.buddygo.buddygoserver.domain.auth.repository.LoginAttemptRepository;
import com.buddygo.buddygoserver.global.exception.CustomException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** 이메일 하나에 대한 비밀번호 대입 공격을 막는다. 윈도우 안에서 maxFailures 번 틀리면 윈도우가 끝날 때까지 잠근다. */
@Component
public class LoginAttemptLimiter {

    private final LoginAttemptRepository repository;
    private final int maxFailures;
    private final Duration window;

    public LoginAttemptLimiter(LoginAttemptRepository repository,
                               @Value("${auth.login-attempt.max-failures}") int maxFailures,
                               @Value("${auth.login-attempt.window}") Duration window) {
        this.repository = repository;
        this.maxFailures = maxFailures;
        this.window = window;
    }

    public void checkBlocked(String email) {
        if (repository.getFailureCount(email) >= maxFailures) {
            throw new CustomException(AuthErrorType.TOO_MANY_LOGIN_ATTEMPTS);
        }
    }

    public void recordFailure(String email) {
        repository.increaseFailure(email, window);
    }

    public void reset(String email) {
        repository.reset(email);
    }
}
