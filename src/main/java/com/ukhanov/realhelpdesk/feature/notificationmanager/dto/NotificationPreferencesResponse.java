package com.ukhanov.realhelpdesk.feature.notificationmanager.dto;

import java.util.Set;

import com.ukhanov.realhelpdesk.core.mail.model.NotificationEvent;

public class NotificationPreferencesResponse {

    private Set<NotificationEvent> events;

    public NotificationPreferencesResponse() {
    }

    public NotificationPreferencesResponse(Set<NotificationEvent> events) {
        this.events = events;
    }

    public Set<NotificationEvent> getEvents() {
        return events;
    }

    public void setEvents(Set<NotificationEvent> events) {
        this.events = events;
    }
}
