package com.ukhanov.realhelpdesk.domain.portal.model;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import com.ukhanov.realhelpdesk.core.security.user.model.UserModel;
import com.ukhanov.realhelpdesk.domain.ticket.model.TicketModel;

import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "portals", uniqueConstraints = {@UniqueConstraint(columnNames = {"owner_id", "name"})})
@Getter
@Setter
public class PortalModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = true, columnDefinition = "TEXT")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private UserModel owner;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "portal_access", joinColumns = @JoinColumn(name = "portal_id"))
    @Column(name = "allowed_user_id")
    private Set<Long> allowedUserIds;

    @OneToMany(mappedBy = "portal", cascade = CascadeType.ALL, orphanRemoval = false, fetch = FetchType.LAZY)
    private Set<TicketModel> tickets = new HashSet<>();

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private boolean isDeleted = false;

    @Version
    private Integer version;

    private Instant timeDelete;

    private boolean isPublic = false;

    @PrePersist
    public void setCreatedAt() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }
}
