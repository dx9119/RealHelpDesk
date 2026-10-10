package com.ukhanov.realhelpdesk.domain.ticket.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Version;

import com.ukhanov.realhelpdesk.core.security.user.model.UserModel;
import com.ukhanov.realhelpdesk.domain.portal.model.PortalModel;

import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
public class TicketModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body; // Описание

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

    @Enumerated(EnumType.STRING)
    private TicketLiveStatus ticketLiveStatus = TicketLiveStatus.ACTIVE;

    private Long whoDelete;

    private Instant timeDelete;

    @Version
    private Integer version;

    @Column(nullable = false)
    TicketAccessStatus accessStatus = TicketAccessStatus.CREATOR_AND_PORTAL_USERS;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    public void setCreatedAt() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }
}
