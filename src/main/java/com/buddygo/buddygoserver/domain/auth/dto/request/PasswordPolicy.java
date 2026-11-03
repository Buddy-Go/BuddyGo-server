package com.buddygo.buddygoserver.domain.auth.dto.request;

final class PasswordPolicy {

    static final String REGEX = "^(?=.*[A-Za-z])(?=.*\\d).{8,64}$";
    static final String MESSAGE = "비밀번호는 영문과 숫자를 포함해 8~64자여야 합니다.";

    private PasswordPolicy() {
    }
}
