package com.buddygo.buddygoserver.global.security.handler;

import com.buddygo.buddygoserver.domain.auth.exception.AuthErrorType;
import com.buddygo.buddygoserver.global.response.error.ErrorResponse;
import com.buddygo.buddygoserver.global.response.error.type.BaseErrorType;
import com.buddygo.buddygoserver.global.response.error.type.GlobalErrorType;
import com.buddygo.buddygoserver.global.security.jwt.JwtExpiration;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 인증 실패(401)를 공통 ErrorResponse 형식으로 내려준다.
 * 토큰 없음 G004 · 만료 A003(재발급 시도) · 그 외 위조/형식 오류 A002(재로그인).
 */
@Component
@RequiredArgsConstructor
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        BaseErrorType error = resolve(authException);
        response.setStatus(error.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        objectMapper.writeValue(response.getOutputStream(), ErrorResponse.from(error));
    }

    private BaseErrorType resolve(AuthenticationException e) {
        if (e instanceof InvalidBearerTokenException) {
            return JwtExpiration.isExpired(e) ? AuthErrorType.EXPIRED_TOKEN : AuthErrorType.INVALID_TOKEN;
        }
        return GlobalErrorType.UNAUTHORIZED;
    }
}
