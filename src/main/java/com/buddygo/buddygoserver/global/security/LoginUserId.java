package com.buddygo.buddygoserver.global.security;

import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 컨트롤러 파라미터에 로그인한 사용자 ID(액세스 토큰의 sub)를 주입한다.
 * 예: {@code public ResponseEntity<?> me(@LoginUserId Long userId)}
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Parameter(hidden = true)
@AuthenticationPrincipal(expression = "T(java.lang.Long).valueOf(subject)")
public @interface LoginUserId {
}
