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

import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "unsubscribed_emails")
@Getter
@Setter
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

    @Column(nullable = false)
    private Instant inStopListAt;

    public UnsubscribedEmail(String email, NotificationEvent muteEvent) {
        this.email = email;
        this.muteEvent = muteEvent;
        this.inStopListAt = Instant.now();
    }

    /** Сбрасывает время выхода из стоп-листа на текущее; Lombok-сеттер с Instant здесь конфликтует по имени — оставлен только он. */
    public void setInStopListAt() {
        this.inStopListAt = Instant.now();
    }
}
