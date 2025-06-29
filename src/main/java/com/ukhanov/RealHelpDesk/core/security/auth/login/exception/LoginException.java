package com.ukhanov.RealHelpDesk.core.security.auth.login.exception;


public class LoginException extends Exception {
    public LoginException(String message) {
        super(message);
    }

    public LoginException(String message, Throwable cause) {
        super(message, cause);
    }

}
