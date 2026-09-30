package com.ukhanov.realhelpdesk.core.security.captcha.exception;

import org.springframework.http.HttpStatus;

import com.ukhanov.realhelpdesk.core.exception.ApiException;

public class CaptchaException extends ApiException {

    public CaptchaException(String message) {
        this(message, HttpStatus.BAD_REQUEST);
    }

    public CaptchaException(String message, Throwable cause) {
        this(message, HttpStatus.BAD_REQUEST, cause);
    }

    public CaptchaException(String message, HttpStatus status) {
        super(message, status);
    }

    public CaptchaException(String message, HttpStatus status, Throwable cause) {
        super(message, status, cause);
    }
}
