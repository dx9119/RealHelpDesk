package com.ukhanov.realhelpdesk.feature.ticketmanager.dto;

import com.ukhanov.realhelpdesk.domain.ticket.model.TicketPriority;
import com.ukhanov.realhelpdesk.domain.ticket.model.TicketStatus;

import java.time.Instant;

public class TicketResponse {
    private Long id;
    private String title;
    private String body;
    private String authorFullName;
    private String assignedUserFullName;
    private String portalName;
    private TicketPriority ticketPriority;
    private TicketStatus ticketStatus;
    private Instant createdAt;

    // Приватный конструктор
    private TicketResponse(Builder builder) {
        this.id = builder.id;
        this.title = builder.title;
        this.body = builder.body;
        this.authorFullName = builder.authorFullName;
        this.assignedUserFullName = builder.assignedUserFullName;
        this.portalName = builder.portalName;
        this.ticketPriority = builder.ticketPriority;
        this.ticketStatus = builder.ticketStatus;
        this.createdAt = builder.createdAt;
    }

    // Геттеры
    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getBody() { return body; }
    public String getAuthorFullName() { return authorFullName; }
    public String getAssignedUserFullName() { return assignedUserFullName; }
    public String getPortalName() { return portalName; }
    public TicketPriority getTicketPriority() { return ticketPriority; }
    public TicketStatus getTicketStatus() { return ticketStatus; }
    public Instant getCreatedAt() { return createdAt; }

    // Билдер
    public static class Builder {
        private Long id;
        private String title;
        private String body;
        private String authorFullName;
        private String assignedUserFullName;
        private String portalName;
        private TicketPriority ticketPriority;
        private TicketStatus ticketStatus;
        private Instant createdAt;

        public Builder id(Long id) {
            this.id = id;
            return this;
        }

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder body(String body) {
            this.body = body;
            return this;
        }

        public Builder authorFullName(String authorFullName) {
            this.authorFullName = authorFullName;
            return this;
        }

        public Builder assignedUserFullName(String assignedUserFullName) {
            this.assignedUserFullName = assignedUserFullName;
            return this;
        }

        public Builder portalName(String portalName) {
            this.portalName = portalName;
            return this;
        }

        public Builder ticketPriority(TicketPriority ticketPriority) {
            this.ticketPriority = ticketPriority;
            return this;
        }

        public Builder ticketStatus(TicketStatus ticketStatus) {
            this.ticketStatus = ticketStatus;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public TicketResponse build() {
            return new TicketResponse(this);
        }
    }
}
