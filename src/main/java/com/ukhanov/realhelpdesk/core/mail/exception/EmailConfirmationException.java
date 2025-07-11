package com.ukhanov.realhelpdesk.core.mail.exception;


public class EmailConfirmationException extends Exception {
    public EmailConfirmationException(String message, Throwable cause) {
        super(message, cause);
    }

    public EmailConfirmationException(String message) {
        super(message);
    }

}
