package com.ukhanov.realhelpdesk.feature.notificationmanager.dto;

import java.util.Set;

import com.ukhanov.realhelpdesk.core.mail.model.NotificationEvent;

/** Фактические настройки пользователя: без сохранённой строки — все события каталога и повтор включён. */
public class NotificationPreferencesResponse {

    private Set<NotificationEvent> events;
    private boolean repeatEnabled;
    private int repeatIntervalMinutes;

    public NotificationPreferencesResponse() {
    }

    public NotificationPreferencesResponse(Set<NotificationEvent> events, boolean repeatEnabled, int repeatIntervalMinutes) {
        this.events = events;
        this.repeatEnabled = repeatEnabled;
        this.repeatIntervalMinutes = repeatIntervalMinutes;
    }

    public Set<NotificationEvent> getEvents() {
        return events;
    }

    public void setEvents(Set<NotificationEvent> events) {
        this.events = events;
    }

    public boolean isRepeatEnabled() {
        return repeatEnabled;
    }

    public void setRepeatEnabled(boolean repeatEnabled) {
        this.repeatEnabled = repeatEnabled;
    }

    public int getRepeatIntervalMinutes() {
        return repeatIntervalMinutes;
    }

    public void setRepeatIntervalMinutes(int repeatIntervalMinutes) {
        this.repeatIntervalMinutes = repeatIntervalMinutes;
    }
}
