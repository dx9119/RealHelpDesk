package com.ukhanov.realhelpdesk.feature.notificationmanager.exception;

import org.springframework.http.HttpStatus;

import com.ukhanov.realhelpdesk.core.exception.ApiException;

public class NotificationException extends ApiException {

    public NotificationException(String message) {
        this(message, HttpStatus.NOT_FOUND);
    }

    public NotificationException(String message, HttpStatus status) {
        super(message, status);
    }

    public static NotificationException notFound(String message) {
        return new NotificationException(message, HttpStatus.NOT_FOUND);
    }

    public static NotificationException badRequest(String message) {
        return new NotificationException(message, HttpStatus.BAD_REQUEST);
    }
}
