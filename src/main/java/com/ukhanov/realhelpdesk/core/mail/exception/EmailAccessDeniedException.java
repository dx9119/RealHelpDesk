package com.ukhanov.realhelpdesk.core.mail.exception;


public class EmailAccessDeniedException extends RuntimeException {
    public EmailAccessDeniedException(String message, Throwable cause) {
        super(message, cause);
    }

    public EmailAccessDeniedException(String message) {
        super(message);
    }

}
