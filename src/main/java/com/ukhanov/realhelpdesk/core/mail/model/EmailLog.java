package com.ukhanov.realhelpdesk.core.mail.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Version;

import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
public class EmailLog {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @Version
    private Integer version;

    private String title;
    private String sender;
    private String recipient;

    @Enumerated(EnumType.STRING)
    private NotificationEvent notificationEvent;

    @Column(nullable = false)
    private LocalDateTime sentAt;

    public EmailLog() {
        this.sentAt = LocalDateTime.now();
    }

    public EmailLog(String title, String sender, String recipient, NotificationEvent notificationEvent) {
        this.title = title;
        this.sender = sender;
        this.recipient = recipient;
        this.notificationEvent = notificationEvent;
        this.sentAt = LocalDateTime.now();
    }
}
