package com.ukhanov.realhelpdesk.core.security.auth.tokens.exception;


public class TokenException extends Exception {
    public TokenException(String message, Throwable cause) {
        super(message, cause);
    }

    public TokenException(String message) {
        super(message);
    }

}
