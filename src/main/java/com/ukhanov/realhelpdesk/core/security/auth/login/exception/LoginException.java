package com.ukhanov.realhelpdesk.core.security.auth.login.exception;

import org.springframework.http.HttpStatus;

import com.ukhanov.realhelpdesk.core.exception.ApiException;

public class LoginException extends ApiException {

    public LoginException(String message) {
        this(message, HttpStatus.UNAUTHORIZED);
    }

    public LoginException(String message, Throwable cause) {
        this(message, HttpStatus.UNAUTHORIZED, cause);
    }

    public LoginException(String message, HttpStatus status) {
        super(message, status);
    }

    public LoginException(String message, HttpStatus status, Throwable cause) {
        super(message, status, cause);
    }
}
