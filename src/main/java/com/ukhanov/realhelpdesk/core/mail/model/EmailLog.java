package com.ukhanov.realhelpdesk.core.mail.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
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

    public String getTitle() {
        return title;
    }

    public LocalDateTime getSentAt() {
        return sentAt;
    }

    public void setSentAt(LocalDateTime sentAt) {
        this.sentAt = sentAt;
    }

    public NotificationEvent getNotificationEvent() {
        return notificationEvent;
    }

    public void setNotificationEvent(NotificationEvent notificationEvent) {
        this.notificationEvent = notificationEvent;
    }

    public void setTitle(String title) {
        title = title;
    }

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public String getRecipient() {
        return recipient;
    }

    public void setRecipient(String recipient) {
        this.recipient = recipient;
    }
}
