package com.buddygo.buddygoserver.global.security.jwt;

public final class JwtExpiration {

    private JwtExpiration() {
    }

    /**
     * 만료 여부. Spring Security 의 JwtTimestampValidator 가 "Jwt expired at ..." 문구로 실패를 알리므로
     * 예외 체인의 메시지로 판단한다. 클라이언트가 만료(재발급 시도)와 위조(재로그인)를 구분하게 하려는 용도다.
     */
    public static boolean isExpired(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t.getMessage() != null && t.getMessage().contains("Jwt expired")) {
                return true;
            }
        }
        return false;
    }
}
