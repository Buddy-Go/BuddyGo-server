package com.buddygo.buddygoserver.global.exception;

import com.buddygo.buddygoserver.global.response.error.ErrorResponse;
import com.buddygo.buddygoserver.global.response.error.type.BaseErrorType;
import com.buddygo.buddygoserver.global.response.error.type.GlobalErrorType;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
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
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

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

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        log.error("[Error Occurred]", e);
        return build(GlobalErrorType.INTERNAL_SERVER_ERROR, ErrorResponse.from(GlobalErrorType.INTERNAL_SERVER_ERROR));
    }

    private ResponseEntity<ErrorResponse> invalidInput(Map<String, String> errors) {
        return build(GlobalErrorType.INVALID_INPUT, ErrorResponse.of(GlobalErrorType.INVALID_INPUT, errors));
    }

    private ResponseEntity<ErrorResponse> build(BaseErrorType error, ErrorResponse body) {
        return ResponseEntity.status(error.getStatus()).body(body);
    }
}
