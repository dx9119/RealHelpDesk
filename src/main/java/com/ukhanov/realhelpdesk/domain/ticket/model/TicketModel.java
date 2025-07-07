package com.ukhanov.realhelpdesk.domain.ticket.model;

import com.ukhanov.realhelpdesk.core.security.user.model.UserModel;
import com.ukhanov.realhelpdesk.domain.portal.model.PortalModel;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
public class TicketModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body; //Описание

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_user_id")
    private UserModel assignedUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false)
    private UserModel author; // создатель заявки

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "portal_id", nullable = false)
    private PortalModel portal;

    @Enumerated(EnumType.STRING)
    private TicketPriority ticketPriority = TicketPriority.NONE;

    @Enumerated(EnumType.STRING)
    private TicketStatus ticketStatus = TicketStatus.OPEN;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    public void setCreatedAt() {
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public UserModel getAssignedUser() {
        return assignedUser;
    }

    public void setAssignedUser(UserModel assignedUser) {
        this.assignedUser = assignedUser;
    }

    public UserModel getAuthor() {
        return author;
    }

    public void setAuthor(UserModel author) {
        this.author = author;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public TicketPriority getTicketPriority() {
        return ticketPriority;
    }

    public void setTicketPriority(TicketPriority ticketPriority) {
        this.ticketPriority = ticketPriority;
    }

    public TicketStatus getTicketStatus() {
        return ticketStatus;
    }

    public void setTicketStatus(TicketStatus ticketStatus) {
        this.ticketStatus = ticketStatus;
    }

    public PortalModel getPortal() {
        return portal;
    }

    public void setPortal(PortalModel portal) {
        this.portal = portal;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

}