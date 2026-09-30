package com.ukhanov.realhelpdesk.feature.messagemanager.exception;

import org.springframework.http.HttpStatus;

import com.ukhanov.realhelpdesk.core.exception.ApiException;

public class MessageException extends ApiException {

    public MessageException(String message) {
        this(message, HttpStatus.CONFLICT);
    }

    public MessageException(String message, Throwable cause) {
        this(message, HttpStatus.CONFLICT, cause);
    }

    public MessageException(String message, HttpStatus status) {
        super(message, status);
    }

    public MessageException(String message, HttpStatus status, Throwable cause) {
        super(message, status, cause);
    }
}
