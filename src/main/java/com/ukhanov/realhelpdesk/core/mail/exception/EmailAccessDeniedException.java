package com.ukhanov.realhelpdesk.core.mail.exception;

import org.springframework.http.HttpStatus;

import com.ukhanov.realhelpdesk.core.exception.ApiRuntimeException;

public class EmailAccessDeniedException extends ApiRuntimeException {

    public EmailAccessDeniedException(String message) {
        this(message, HttpStatus.FORBIDDEN);
    }

    public EmailAccessDeniedException(String message, Throwable cause) {
        this(message, HttpStatus.FORBIDDEN, cause);
    }

    public EmailAccessDeniedException(String message, HttpStatus status) {
        super(message, status);
    }

    public EmailAccessDeniedException(String message, HttpStatus status, Throwable cause) {
        super(message, status, cause);
    }
}
