package com.ukhanov.realhelpdesk.core.exception;

import org.springframework.http.HttpStatus;

import lombok.Getter;

@Getter
public class ApiException extends Exception {

    private final HttpStatus status;

    public ApiException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    public ApiException(String message, HttpStatus status, Throwable cause) {
        super(message, cause);
        this.status = status;
    }
}
