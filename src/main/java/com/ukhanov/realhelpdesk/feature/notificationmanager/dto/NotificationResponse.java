package com.ukhanov.realhelpdesk.feature.notificationmanager.dto;

import java.time.Instant;

import com.ukhanov.realhelpdesk.core.mail.model.NotificationEvent;

public class NotificationResponse {

    private Long id;
    private NotificationEvent event;
    private Long ticketId;
    private Long portalId;
    private String title;
    private boolean read;
    private Instant createdAt;

    public NotificationResponse() {
    }

    public NotificationResponse(Long id, NotificationEvent event, Long ticketId, Long portalId, String title, boolean read,
            Instant createdAt) {
        this.id = id;
        this.event = event;
        this.ticketId = ticketId;
        this.portalId = portalId;
        this.title = title;
        this.read = read;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public NotificationEvent getEvent() {
        return event;
    }

    public void setEvent(NotificationEvent event) {
        this.event = event;
    }

    public Long getTicketId() {
        return ticketId;
    }

    public void setTicketId(Long ticketId) {
        this.ticketId = ticketId;
    }

    public Long getPortalId() {
        return portalId;
    }

    public void setPortalId(Long portalId) {
        this.portalId = portalId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public boolean isRead() {
        return read;
    }

    public void setRead(boolean read) {
        this.read = read;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
