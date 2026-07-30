package com.otboo.global.error;

import jakarta.validation.ConstraintViolationException;
import java.util.LinkedHashMap;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.bind.MissingRequestCookieException;
import org.springframework.security.access.AccessDeniedException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(OtbooException.class)
    public ResponseEntity<ErrorResponse> handleOtbooException(
            OtbooException exception
    ) {
        ErrorResponse response = new ErrorResponse(
                exception.getClass().getSimpleName(),
                exception.getMessage(),
                Map.of()
        );

        return ResponseEntity
                .status(exception.getStatus())
                .body(response);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException exception
    ) {
        Map<String, String> details = new LinkedHashMap<>();

        for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
            details.putIfAbsent(
                    fieldError.getField(),
                    getErrorMessage(fieldError)
            );
        }

        ErrorResponse response = new ErrorResponse(
                exception.getClass().getSimpleName(),
                "요청 값이 올바르지 않습니다.",
                details
        );

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(response);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleHttpRequestMethodNotSupportedException(
            HttpRequestMethodNotSupportedException exception
    ) {
        ErrorResponse response = new ErrorResponse(
                exception.getClass().getSimpleName(),
                "지원하지 않는 HTTP 메서드입니다.",
                Map.of()
        );

        return ResponseEntity
                .status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(response);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadableException(
            HttpMessageNotReadableException exception
    ) {
        ErrorResponse response = new ErrorResponse(
                exception.getClass().getSimpleName(),
                "요청 본문을 읽을 수 없습니다.",
                Map.of()
        );

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(response);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingServletRequestParameterException(
            MissingServletRequestParameterException exception
    ) {
        ErrorResponse response = new ErrorResponse(
                exception.getClass().getSimpleName(),
                "필수 요청 파라미터가 누락되었습니다.",
                Map.of("parameter", exception.getParameterName())
        );

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(response);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentTypeMismatchException(
            MethodArgumentTypeMismatchException exception
    ) {
        Map<String, String> details = new LinkedHashMap<>();
        details.put("parameter", exception.getName());
        details.put("value", String.valueOf(exception.getValue()));

        ErrorResponse response = new ErrorResponse(
                exception.getClass().getSimpleName(),
                "요청 값의 타입이 올바르지 않습니다.",
                details
        );

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(response);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFoundException(
            NoResourceFoundException exception
    ) {
        ErrorResponse response = new ErrorResponse(
                exception.getClass().getSimpleName(),
                "요청한 리소스를 찾을 수 없습니다.",
                Map.of()
        );

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(response);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(
            AccessDeniedException exception
    ) {
        ErrorResponse response = new ErrorResponse(
                exception.getClass().getSimpleName(), "접근 권한이 없습니다.", Map.of()
        );

        return  ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(
            Exception exception
    ) {
        log.error("처리되지 않은 서버 예외가 발생했습니다.", exception);

        ErrorResponse response = new ErrorResponse(
                exception.getClass().getSimpleName(),
                "서버 내부 오류가 발생했습니다.",
                Map.of()
        );

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(response);
    }

    private String getErrorMessage(FieldError fieldError) {
        String defaultMessage = fieldError.getDefaultMessage();

        if (defaultMessage == null || defaultMessage.isBlank()) {
            return "잘못된 값입니다.";
        }

        return defaultMessage;
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolationException(
        ConstraintViolationException e
    ) {
        ErrorResponse response = new ErrorResponse(
            e.getClass().getSimpleName(),
            "요청 값이 올바르지 않습니다.",
            Map.of()
        );

      return ResponseEntity
          .status(HttpStatus.BAD_REQUEST)
          .body(response);
    }

    @ExceptionHandler(MissingRequestCookieException.class)
    public ResponseEntity<ErrorResponse> handleMissingRequestCookieException(
        MissingRequestCookieException exception
    ) {
        ErrorResponse response = new ErrorResponse(
            exception.getClass().getSimpleName(),
            "필수 쿠키가 누락되었습니다.",
            Map.of("cookie", exception.getCookieName())
        );

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(response);
    }
}