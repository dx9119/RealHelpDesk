package com.ukhanov.realhelpdesk.feature.ticketmanager.exception;

import org.springframework.http.HttpStatus;

import com.ukhanov.realhelpdesk.core.exception.ApiException;

public class TicketException extends ApiException {

    public TicketException(String message) {
        this(message, HttpStatus.FORBIDDEN);
    }

    public TicketException(String message, Throwable cause) {
        this(message, HttpStatus.FORBIDDEN, cause);
    }

    public TicketException(String message, HttpStatus status) {
        super(message, status);
    }

    public TicketException(String message, HttpStatus status, Throwable cause) {
        super(message, status, cause);
    }

    public static TicketException notFound(String message) {
        return new TicketException(message, HttpStatus.NOT_FOUND);
    }
}
