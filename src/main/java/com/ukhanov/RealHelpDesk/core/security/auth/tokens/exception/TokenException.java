package com.ukhanov.RealHelpDesk.core.security.auth.tokens.exception;


public class TokenException extends RuntimeException {
    public TokenException(String message, Throwable cause) {
        super(message, cause);
    }

    public TokenException(String message) {
        super(message);
    }

}
