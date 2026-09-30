package com.ukhanov.realhelpdesk.core.security.auth.refresh.exception;

import org.springframework.http.HttpStatus;

import com.ukhanov.realhelpdesk.core.exception.ApiException;

public class RefreshException extends ApiException {

    public RefreshException(String message) {
        this(message, HttpStatus.UNAUTHORIZED);
    }

    public RefreshException(String message, Throwable cause) {
        this(message, HttpStatus.UNAUTHORIZED, cause);
    }

    public RefreshException(String message, HttpStatus status) {
        super(message, status);
    }

    public RefreshException(String message, HttpStatus status, Throwable cause) {
        super(message, status, cause);
    }
}
