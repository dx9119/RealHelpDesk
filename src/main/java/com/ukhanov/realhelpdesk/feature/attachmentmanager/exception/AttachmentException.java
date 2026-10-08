package com.ukhanov.realhelpdesk.feature.attachmentmanager.exception;

import org.springframework.http.HttpStatus;

import com.ukhanov.realhelpdesk.core.exception.ApiException;

public class AttachmentException extends ApiException {

    public AttachmentException(String message) {
        this(message, HttpStatus.CONFLICT, null);
    }

    public AttachmentException(String message, HttpStatus status) {
        this(message, status, null);
    }

    public AttachmentException(String message, HttpStatus status, Throwable cause) {
        super(message, status, cause);
    }

    /** Файл, заявка или сообщение не найдено (или не принадлежат порталу из пути) — 404, без перечисления чужих ID. */
    public static AttachmentException notFound(String message) {
        return new AttachmentException(message, HttpStatus.NOT_FOUND, null);
    }

    /** Некорректный файл или текст сообщения — 400. */
    public static AttachmentException badRequest(String message) {
        return new AttachmentException(message, HttpStatus.BAD_REQUEST, null);
    }
}
