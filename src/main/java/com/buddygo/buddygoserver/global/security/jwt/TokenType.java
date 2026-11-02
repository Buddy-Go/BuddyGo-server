package com.buddygo.buddygoserver.global.security.jwt;

/** 액세스 토큰과 리프레시 토큰을 서로 대신 쓰지 못하게 구분하는 클레임 값 */
public enum TokenType {
    ACCESS,
    REFRESH;

    public static final String CLAIM = "token_type";
}
