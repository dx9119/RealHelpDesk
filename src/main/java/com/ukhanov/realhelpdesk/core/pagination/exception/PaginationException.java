package com.ukhanov.realhelpdesk.core.pagination.exception;

import org.springframework.http.HttpStatus;

import com.ukhanov.realhelpdesk.core.exception.ApiRuntimeException;

public class PaginationException extends ApiRuntimeException {

    public PaginationException(String message) {
        this(message, HttpStatus.BAD_REQUEST);
    }

    public PaginationException(String message, HttpStatus status) {
        super(message, status);
    }

    public PaginationException(String message, HttpStatus status, Throwable cause) {
        super(message, status, cause);
    }
}
