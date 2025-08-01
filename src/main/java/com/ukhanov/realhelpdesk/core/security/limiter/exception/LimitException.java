package com.ukhanov.realhelpdesk.core.security.limiter.exception;


public class LimitException extends Exception {
    public LimitException(String message, Throwable cause) {
        super(message, cause);
    }

    public LimitException(String message) {
        super(message);
    }

}
