package com.ukhanov.realhelpdesk.feature.messagemanager.exception;


public class MessageException extends Exception {
    public MessageException(String message, Throwable cause) {
        super(message, cause);
    }

    public MessageException(String message) {
        super(message);
    }

}
