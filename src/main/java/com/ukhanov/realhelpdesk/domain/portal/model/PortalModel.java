package com.ukhanov.realhelpdesk.domain.portal.model;

import com.ukhanov.realhelpdesk.core.security.user.model.UserModel;
import com.ukhanov.realhelpdesk.domain.ticket.model.TicketModel;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "portals")
public class PortalModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(nullable = true, columnDefinition = "TEXT")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private UserModel owner;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "portal_access", joinColumns = @JoinColumn(name = "portal_id"))
    @Column(name = "allowed_user_id")
    private Set<UUID> allowedUserIds;

    @OneToMany(mappedBy = "portal", cascade = CascadeType.ALL, orphanRemoval = false, fetch = FetchType.LAZY)
    private Set<TicketModel> tickets = new HashSet<>();

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private boolean isPublic = false;

    @PrePersist
    public void setCreatedAt() {
        this.createdAt = Instant.now();
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public boolean isPublic() {
        return isPublic;
    }

    public void setPublic(boolean aPublic) {
        isPublic = aPublic;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public UserModel getOwner() {
        return owner;
    }

    public Set<UUID> getAllowedUserIds() {
        return allowedUserIds;
    }

    public void setAllowedUserIds(Set<UUID> allowedUserIds) {
        this.allowedUserIds = allowedUserIds;
    }

    public void setOwner(UserModel owner) {
        this.owner = owner;
    }

    public Set<TicketModel> getTickets() {
        return tickets;
    }

    public void setTickets(Set<TicketModel> tickets) {
        this.tickets = tickets;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}