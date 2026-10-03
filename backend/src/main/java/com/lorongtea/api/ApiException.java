package com.lorongtea.api;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {
    private final HttpStatus status;
    private final String code;
    private final HttpHeaders headers;

    public ApiException(HttpStatus status, String code, String message) {
        this(status, code, message, HttpHeaders.EMPTY);
    }

    public ApiException(HttpStatus status, String code, String message, HttpHeaders headers) {
        super(message);
        this.status = status;
        this.code = code;
        this.headers = HttpHeaders.readOnlyHttpHeaders(headers);
    }

    public HttpStatus status() {
        return status;
    }

    HttpHeaders headers() {
        return headers;
    }

    public String code() {
        return code;
    }
}
