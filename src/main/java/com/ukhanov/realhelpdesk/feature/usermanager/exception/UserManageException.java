package com.ukhanov.realhelpdesk.feature.usermanager.exception;

import org.springframework.http.HttpStatus;

import com.ukhanov.realhelpdesk.core.exception.ApiException;

public class UserManageException extends ApiException {

    public UserManageException(String message) {
        this(message, HttpStatus.FORBIDDEN);
    }

    public UserManageException(String message, Throwable cause) {
        this(message, HttpStatus.FORBIDDEN, cause);
    }

    public UserManageException(String message, HttpStatus status) {
        super(message, status);
    }

    public UserManageException(String message, HttpStatus status, Throwable cause) {
        super(message, status, cause);
    }
}
