package com.ukhanov.realhelpdesk.domain.message.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Version;

import com.ukhanov.realhelpdesk.core.security.user.model.UserModel;
import com.ukhanov.realhelpdesk.domain.ticket.model.TicketModel;

@Entity
public class MessageModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_id", nullable = false)
    private TicketModel ticket;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false)
    private UserModel author;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String messageText; // Текст сообщения

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Version
    private Integer version;

    @PrePersist
    public void setCreatedAt() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public TicketModel getTicket() {
        return ticket;
    }

    public void setTicket(TicketModel ticket) {
        this.ticket = ticket;
    }

    public UserModel getAuthor() {
        return author;
    }

    public void setAuthor(UserModel author) {
        this.author = author;
    }

    public String getMessageText() {
        return messageText;
    }

    public void setMessageText(String messageText) {
        this.messageText = messageText;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

}
