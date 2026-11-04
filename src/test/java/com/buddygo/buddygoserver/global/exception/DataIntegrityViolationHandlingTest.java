package com.buddygo.buddygoserver.global.exception;

import com.buddygo.buddygoserver.domain.user.exception.UserErrorType;
import com.buddygo.buddygoserver.global.response.error.ErrorResponse;
import org.hibernate.exception.ConstraintViolationException;
import org.hibernate.exception.ConstraintViolationException.ConstraintKind;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DataIntegrityViolationHandlingTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler(List.of(
            () -> Map.of("uk_users_email", UserErrorType.DUPLICATE_EMAIL)));

    @Test
    void 등록된_유니크_제약은_도메인_에러로_바꾼다() {
        ResponseEntity<ErrorResponse> response = handler.handleDataIntegrityViolation(
                violation(ConstraintKind.UNIQUE, "uk_users_email"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().code()).isEqualTo("U002");
    }

    @Test
    void 등록되지_않은_유니크_제약은_500() {
        ResponseEntity<ErrorResponse> response = handler.handleDataIntegrityViolation(
                violation(ConstraintKind.UNIQUE, "uk_places_kakao_place_id"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void 유니크가_아닌_위반은_이름이_같아도_500() {
        ResponseEntity<ErrorResponse> response = handler.handleDataIntegrityViolation(
                violation(ConstraintKind.FOREIGN_KEY, "uk_users_email"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void 같은_제약을_두_도메인이_등록하면_기동에_실패한다() {
        UniqueConstraintErrors errors = () -> Map.of("uk_users_email", UserErrorType.DUPLICATE_EMAIL);

        assertThatThrownBy(() -> new GlobalExceptionHandler(List.of(errors, errors)))
                .isInstanceOf(IllegalStateException.class);
    }

    private DataIntegrityViolationException violation(ConstraintKind kind, String constraint) {
        return new DataIntegrityViolationException("violation", new ConstraintViolationException(
                "violation", new SQLException("violation"), kind, constraint));
    }
}
