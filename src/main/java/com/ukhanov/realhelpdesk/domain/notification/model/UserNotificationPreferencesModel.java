package com.ukhanov.realhelpdesk.domain.notification.model;

import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import com.ukhanov.realhelpdesk.core.mail.model.NotificationEvent;

/**
 * Настройки in-app оповещений пользователя: набор событий, о которых он хочет получать оповещения. Строки нет — включены все события из
 * каталога in-app (opt-out); email-мьют ({@code unsubscribed_emails}) от этих настроек не зависит.
 */
@Entity
@Table(name = "user_notification_preferences")
public class UserNotificationPreferencesModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_notification_preferences_events", joinColumns = @JoinColumn(name = "preferences_id"))
    @Column(name = "event", nullable = false)
    @Enumerated(EnumType.STRING)
    private Set<NotificationEvent> enabledEvents = new HashSet<>();

    protected UserNotificationPreferencesModel() {
    }

    public UserNotificationPreferencesModel(Long userId, Set<NotificationEvent> enabledEvents) {
        this.userId = userId;
        this.enabledEvents = new HashSet<>(enabledEvents);
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Set<NotificationEvent> getEnabledEvents() {
        return enabledEvents;
    }

    public void setEnabledEvents(Set<NotificationEvent> enabledEvents) {
        this.enabledEvents = new HashSet<>(enabledEvents);
    }
}
