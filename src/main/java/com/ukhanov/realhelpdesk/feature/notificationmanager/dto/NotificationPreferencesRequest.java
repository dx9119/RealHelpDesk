package com.ukhanov.realhelpdesk.feature.notificationmanager.dto;

import java.util.Set;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import com.ukhanov.realhelpdesk.core.mail.model.NotificationEvent;
import com.ukhanov.realhelpdesk.domain.notification.model.UserNotificationPreferencesModel;

public class NotificationPreferencesRequest {

    @NotNull(message = "Список событий обязателен")
    @NotEmpty(message = "Список событий не может быть пустым")
    private Set<NotificationEvent> events;

    /** Повторять непрочитанные NEW_TICKET/NEW_MESSAGE; null — не менять (при создании — включён). */
    private Boolean repeatEnabled;

    /** Интервал между повторами в минутах; null — не менять (при создании — 30). */
    @Min(value = UserNotificationPreferencesModel.MIN_REPEAT_INTERVAL_MINUTES, message = "Интервал повтора от 1 минуты")
    @Max(value = UserNotificationPreferencesModel.MAX_REPEAT_INTERVAL_MINUTES, message = "Интервал повтора не больше недели")
    private Integer repeatIntervalMinutes;

    public NotificationPreferencesRequest() {
    }

    public NotificationPreferencesRequest(Set<NotificationEvent> events) {
        this.events = events;
    }

    public NotificationPreferencesRequest(Set<NotificationEvent> events, Boolean repeatEnabled, Integer repeatIntervalMinutes) {
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

    public Boolean getRepeatEnabled() {
        return repeatEnabled;
    }

    public void setRepeatEnabled(Boolean repeatEnabled) {
        this.repeatEnabled = repeatEnabled;
    }

    public Integer getRepeatIntervalMinutes() {
        return repeatIntervalMinutes;
    }

    public void setRepeatIntervalMinutes(Integer repeatIntervalMinutes) {
        this.repeatIntervalMinutes = repeatIntervalMinutes;
    }
}
