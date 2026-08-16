package com.lorongtea.api;

import com.lorongtea.catalog.StoreNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(StoreNotFoundException.class)
    ResponseEntity<Object> storeNotFound(StoreNotFoundException exception, WebRequest request) {
        return handleExceptionInternal(exception, error("STORE_NOT_FOUND", "Store not found", request),
                HttpHeaders.EMPTY, HttpStatus.NOT_FOUND, request);
    }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<Object> apiError(ApiException exception, WebRequest request) {
        return handleExceptionInternal(exception, error(exception.code(), exception.getMessage(), request),
                HttpHeaders.EMPTY, exception.status(), request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> unexpectedError(Exception exception, WebRequest request) {
        log.error("Unhandled request error", exception);
        return handleExceptionInternal(exception, null, HttpHeaders.EMPTY,
                HttpStatus.INTERNAL_SERVER_ERROR, request);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception exception, Object body,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ApiError apiError = body instanceof ApiError error ? error : switch (status.value()) {
            case 400 -> error("INVALID_REQUEST", "Invalid request", request);
            case 404 -> error("NOT_FOUND", "Resource not found", request);
            case 500 -> error("INTERNAL_ERROR", "Internal server error", request);
            default -> error("HTTP_" + status.value(), "Request failed", request);
        };
        return super.handleExceptionInternal(exception, apiError, headers, status, request);
    }

    private ApiError error(String code, String message, WebRequest request) {
        String requestId = (String) request.getAttribute(RequestIdFilter.ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
        return new ApiError(code, message, requestId);
    }

    record ApiError(String code, String message, String requestId) {
    }
}
