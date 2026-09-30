package com.ukhanov.realhelpdesk.core.security.auth.tokens.exception;

import org.springframework.http.HttpStatus;

import com.ukhanov.realhelpdesk.core.exception.ApiException;

public class TokenException extends ApiException {

    public TokenException(String message) {
        this(message, HttpStatus.UNAUTHORIZED, null);
    }

    public TokenException(String message, Throwable cause) {
        this(message, HttpStatus.UNAUTHORIZED, cause);
    }

    public TokenException(String message, HttpStatus status, Throwable cause) {
        super(message, status, cause);
    }
}
