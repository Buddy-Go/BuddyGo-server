package com.buddygo.buddygoserver.global.exception;

import com.buddygo.buddygoserver.global.response.error.ErrorResponse;
import com.buddygo.buddygoserver.global.response.error.type.BaseErrorType;
import com.buddygo.buddygoserver.global.response.error.type.GlobalErrorType;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException.ConstraintKind;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 유니크 제약 이름 → 에러 타입. 같은 이름을 두 도메인이 등록하면 기동에 실패한다. */
    private final Map<String, BaseErrorType> uniqueConstraintErrors;

    public GlobalExceptionHandler(List<UniqueConstraintErrors> uniqueConstraintErrors) {
        this.uniqueConstraintErrors = uniqueConstraintErrors.stream()
                .flatMap(errors -> errors.byConstraint().entrySet().stream())
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @ExceptionHandler(CustomException.class)
    public ResponseEntity<ErrorResponse> handleCustom(CustomException e) {
        BaseErrorType error = e.getErrorType();
        log.warn("[Business Warning] {} {}", error.getCode(), e.getMessage());
        return build(error, ErrorResponse.of(error, e.getMessage()));
    }

    /** @RequestBody 검증 실패. 한 필드에 검증이 여러 개 걸리면 먼저 걸린 하나만 담는다. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleBodyValidation(MethodArgumentNotValidException e) {
        Map<String, String> errors = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors()
                .forEach(f -> errors.putIfAbsent(f.getField(), f.getDefaultMessage()));
        return invalidInput(errors);
    }

    /** @RequestParam · @PathVariable 검증 실패 */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleMethodValidation(HandlerMethodValidationException e) {
        Map<String, String> errors = new LinkedHashMap<>();
        e.getParameterValidationResults().forEach(r -> r.getResolvableErrors().forEach(err ->
                errors.putIfAbsent(r.getMethodParameter().getParameterName(), err.getDefaultMessage())));
        return invalidInput(errors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException e) {
        Map<String, String> errors = new LinkedHashMap<>();
        e.getConstraintViolations()
                .forEach(v -> errors.putIfAbsent(v.getPropertyPath().toString(), v.getMessage()));
        return invalidInput(errors);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParam(MissingServletRequestParameterException e) {
        return invalidInput(Map.of(e.getParameterName(), "필수 파라미터입니다."));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return invalidInput(Map.of(e.getName(), "값의 형식이 올바르지 않습니다."));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleNotReadable(HttpMessageNotReadableException e) {
        return build(GlobalErrorType.MALFORMED_REQUEST, ErrorResponse.from(GlobalErrorType.MALFORMED_REQUEST));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotAllowed(HttpRequestMethodNotSupportedException e) {
        return build(GlobalErrorType.METHOD_NOT_ALLOWED, ErrorResponse.from(GlobalErrorType.METHOD_NOT_ALLOWED));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException e) {
        return build(GlobalErrorType.RESOURCE_NOT_FOUND, ErrorResponse.from(GlobalErrorType.RESOURCE_NOT_FOUND));
    }

    /**
     * 사전 검사를 동시 요청이 함께 통과해 유니크 제약에 걸린 경우. 등록된 제약만 도메인 에러로 바꾸고,
     * 등록되지 않은 제약과 FK·NOT NULL·CHECK 위반은 버그로 보고 500 으로 둔다.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException e) {
        String constraint = violatedUniqueConstraint(e);
        BaseErrorType error = constraint == null ? null : uniqueConstraintErrors.get(constraint);
        if (error == null) {
            return handleUnexpected(e);
        }
        log.warn("[Unique Violation] {} {}", error.getCode(), constraint);
        return build(error, ErrorResponse.from(error));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        log.error("[Error Occurred]", e);
        return build(GlobalErrorType.INTERNAL_SERVER_ERROR, ErrorResponse.from(GlobalErrorType.INTERNAL_SERVER_ERROR));
    }

    private static String violatedUniqueConstraint(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof org.hibernate.exception.ConstraintViolationException cve
                    && cve.getKind() == ConstraintKind.UNIQUE) {
                return cve.getConstraintName();
            }
        }
        return null;
    }

    private ResponseEntity<ErrorResponse> invalidInput(Map<String, String> errors) {
        return build(GlobalErrorType.INVALID_INPUT, ErrorResponse.of(GlobalErrorType.INVALID_INPUT, errors));
    }

    private ResponseEntity<ErrorResponse> build(BaseErrorType error, ErrorResponse body) {
        return ResponseEntity.status(error.getStatus()).body(body);
    }
}
