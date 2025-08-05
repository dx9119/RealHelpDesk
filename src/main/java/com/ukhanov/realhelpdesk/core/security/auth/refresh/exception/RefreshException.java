package com.ukhanov.realhelpdesk.core.security.auth.refresh.exception;


public class RefreshException extends Exception {
    public RefreshException(String message) {
        super(message);
    }

    public RefreshException(String message, Throwable cause) {
        super(message, cause);
    }

}
