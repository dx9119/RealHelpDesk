package com.ukhanov.realhelpdesk.domain.portal.model;

/**
 * События истории портала: передачи владения и изменения самого портала (имя, описание, видимость, состав участников, удаление). Создание
 * портала в историю не пишется.
 */
public enum PortalHistoryEvent {
    TRANSFER_REQUESTED, TRANSFER_ACCEPTED, TRANSFER_REJECTED, TRANSFER_CANCELLED, TRANSFER_EXPIRED, NAME_CHANGED, DESCRIPTION_CHANGED,
    VISIBILITY_CHANGED, USERS_CHANGED, PORTAL_DELETED
}
