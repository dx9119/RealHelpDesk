package com.ukhanov.realhelpdesk.core.exception;

import org.springframework.http.HttpStatus;

public class ApiRuntimeException extends RuntimeException {

    private final HttpStatus status;

    public ApiRuntimeException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    public ApiRuntimeException(String message, HttpStatus status, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
