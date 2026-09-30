package com.ukhanov.realhelpdesk.feature.portalmanager.exception;

import org.springframework.http.HttpStatus;

import com.ukhanov.realhelpdesk.core.exception.ApiException;

public class PortalException extends ApiException {

    public PortalException(String message) {
        this(message, HttpStatus.CONFLICT);
    }

    public PortalException(String message, Throwable cause) {
        this(message, HttpStatus.CONFLICT, cause);
    }

    public PortalException(String message, HttpStatus status) {
        super(message, status);
    }

    public PortalException(String message, HttpStatus status, Throwable cause) {
        super(message, status, cause);
    }

    public static PortalException notFound(String message) {
        return new PortalException(message, HttpStatus.NOT_FOUND);
    }
}
