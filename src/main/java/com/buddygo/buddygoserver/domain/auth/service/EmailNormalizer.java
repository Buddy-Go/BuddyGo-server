package com.buddygo.buddygoserver.domain.auth.service;

import java.util.Locale;

final class EmailNormalizer {

    private EmailNormalizer() {
    }

    /** 대소문자만 다른 이메일로 중복 가입하지 못하게 소문자로 맞춘다. */
    static String normalize(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }
}
