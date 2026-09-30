package com.ukhanov.realhelpdesk.core.security.limiter.exception;

import org.springframework.http.HttpStatus;

import com.ukhanov.realhelpdesk.core.exception.ApiException;

public class LimitException extends ApiException {

    public LimitException(String message) {
        this(message, HttpStatus.CONFLICT);
    }

    public LimitException(String message, Throwable cause) {
        this(message, HttpStatus.CONFLICT, cause);
    }

    public LimitException(String message, HttpStatus status) {
        super(message, status);
    }

    public LimitException(String message, HttpStatus status, Throwable cause) {
        super(message, status, cause);
    }
}
