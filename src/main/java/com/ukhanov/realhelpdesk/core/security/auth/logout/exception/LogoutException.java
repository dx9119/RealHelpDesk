package com.ukhanov.realhelpdesk.core.security.auth.logout.exception;

import org.springframework.http.HttpStatus;

import com.ukhanov.realhelpdesk.core.exception.ApiException;

public class LogoutException extends ApiException {

    public LogoutException(String message) {
        this(message, HttpStatus.UNAUTHORIZED);
    }

    public LogoutException(String message, HttpStatus status) {
        super(message, status);
    }

    public LogoutException(String message, HttpStatus status, Throwable cause) {
        super(message, status, cause);
    }
}
