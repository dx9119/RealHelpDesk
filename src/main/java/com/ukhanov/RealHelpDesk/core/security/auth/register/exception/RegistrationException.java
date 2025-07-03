package com.ukhanov.RealHelpDesk.core.security.auth.register.exception;


public class RegistrationException extends RuntimeException {
    public RegistrationException(String message, Throwable cause) {
        super(message, cause);
    }

    public RegistrationException(String message) {
        super(message);
    }

}
