package com.ukhanov.realhelpdesk.domain.portal.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * Запрос владельца на передачу владения порталом другому пользователю. Пока статус PENDING — новый владелец может подтвердить или отклонить
 * запрос своим паролем; активных запросов на портал не больше одного (частичный уникальный индекс в БД).
 */
@Entity
@Table(name = "portal_transfer_requests")
public class PortalTransferRequestModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "portal_id", nullable = false)
    private Long portalId;

    /** Текущий владелец, инициировавший передачу. */
    @Column(name = "initiator_id", nullable = false)
    private Long initiatorId;

    /** Пользователь, которого предлагают сделать владельцем. */
    @Column(name = "proposed_owner_id", nullable = false)
    private Long proposedOwnerId;

    @Column(name = "reason_initiator", nullable = false, length = 2000)
    private String reasonInitiator;

    /** Причина подтверждения или отклонения — слово нового владельца; пуста, пока запрос активен. */
    @Column(name = "reason_proposed", length = 2000)
    private String reasonProposed;

    @Column(name = "keep_old_owner", nullable = false)
    private boolean keepOldOwner;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PortalTransferStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Срок действия: после него запрос лениво закрывается как EXPIRED при следующем обращении. */
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @PrePersist
    public void setCreatedAt() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getPortalId() {
        return portalId;
    }

    public void setPortalId(Long portalId) {
        this.portalId = portalId;
    }

    public Long getInitiatorId() {
        return initiatorId;
    }

    public void setInitiatorId(Long initiatorId) {
        this.initiatorId = initiatorId;
    }

    public Long getProposedOwnerId() {
        return proposedOwnerId;
    }

    public void setProposedOwnerId(Long proposedOwnerId) {
        this.proposedOwnerId = proposedOwnerId;
    }

    public String getReasonInitiator() {
        return reasonInitiator;
    }

    public void setReasonInitiator(String reasonInitiator) {
        this.reasonInitiator = reasonInitiator;
    }

    public String getReasonProposed() {
        return reasonProposed;
    }

    public void setReasonProposed(String reasonProposed) {
        this.reasonProposed = reasonProposed;
    }

    public boolean isKeepOldOwner() {
        return keepOldOwner;
    }

    public void setKeepOldOwner(boolean keepOldOwner) {
        this.keepOldOwner = keepOldOwner;
    }

    public PortalTransferStatus getStatus() {
        return status;
    }

    public void setStatus(PortalTransferStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public void setDecidedAt(Instant decidedAt) {
        this.decidedAt = decidedAt;
    }
}
