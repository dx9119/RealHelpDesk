package com.ukhanov.realhelpdesk.feature.notificationmanager.dto;

import java.util.Set;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import com.ukhanov.realhelpdesk.core.mail.model.NotificationEvent;

public class NotificationPreferencesRequest {

    @NotNull(message = "Список событий обязателен")
    @NotEmpty(message = "Список событий не может быть пустым")
    private Set<NotificationEvent> events;

    public NotificationPreferencesRequest() {
    }

    public NotificationPreferencesRequest(Set<NotificationEvent> events) {
        this.events = events;
    }

    public Set<NotificationEvent> getEvents() {
        return events;
    }

    public void setEvents(Set<NotificationEvent> events) {
        this.events = events;
    }
}
