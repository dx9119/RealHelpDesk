package com.ukhanov.RealHelpDesk.feature.ticketmanager.exception;


public class TicketException extends Exception {
    public TicketException(String message, Throwable cause) {
        super(message, cause);
    }

    public TicketException(String message) {
        super(message);
    }

}
