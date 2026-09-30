package com.ukhanov.realhelpdesk.core.mail.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "unsubscribed_emails")
public class UnsubscribedEmail {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String email;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private NotificationEvent muteEvent = NotificationEvent.NONE;

    public UnsubscribedEmail() {
    }

    @Column(nullable = false, updatable = false)
    private Instant inStopListAt;

    public UnsubscribedEmail(String email, NotificationEvent muteEvent) {
        this.email = email;
        this.muteEvent = muteEvent;
        this.inStopListAt = Instant.now();
    }

    public String getEmail() {
        return email;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getId() {
        return id;
    }

    public NotificationEvent getMuteEvent() {
        return muteEvent;
    }

    public void setMuteEvent(NotificationEvent muteEvent) {
        this.muteEvent = muteEvent;
    }

    public void setInStopListAt(Instant inStopListAt) {
        this.inStopListAt = inStopListAt;
    }

    public void setInStopListAt() {
        this.inStopListAt = Instant.now();
    }

}
