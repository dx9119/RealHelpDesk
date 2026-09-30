package com.ukhanov.realhelpdesk.core.security.auth.register.exception;

import org.springframework.http.HttpStatus;

import com.ukhanov.realhelpdesk.core.exception.ApiException;

public class RegistrationException extends ApiException {

    public RegistrationException(String message) {
        this(message, HttpStatus.CONFLICT);
    }

    public RegistrationException(String message, Throwable cause) {
        this(message, HttpStatus.CONFLICT, cause);
    }

    public RegistrationException(String message, HttpStatus status) {
        super(message, status);
    }

    public RegistrationException(String message, HttpStatus status, Throwable cause) {
        super(message, status, cause);
    }
}
