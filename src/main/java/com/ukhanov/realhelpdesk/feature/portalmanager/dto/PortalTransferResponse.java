package com.ukhanov.realhelpdesk.feature.portalmanager.dto;

import java.time.Instant;

/** Запрос на передачу портала: виден инициатору (владельцу) и предлагаемому владельцу — для решения о подтверждении. */
public class PortalTransferResponse {

    private Long id;
    private Long portalId;
    private String portalName;
    private String initiatorName;
    private String proposedOwnerEmail;
    private String reasonInitiator;
    private String reasonProposed;
    private boolean keepOldOwnerAsMember;
    private String status;
    private Instant createdAt;
    private Instant expiresAt;
    private Instant decidedAt;

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

    public String getPortalName() {
        return portalName;
    }

    public void setPortalName(String portalName) {
        this.portalName = portalName;
    }

    public String getInitiatorName() {
        return initiatorName;
    }

    public void setInitiatorName(String initiatorName) {
        this.initiatorName = initiatorName;
    }

    public String getProposedOwnerEmail() {
        return proposedOwnerEmail;
    }

    public void setProposedOwnerEmail(String proposedOwnerEmail) {
        this.proposedOwnerEmail = proposedOwnerEmail;
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

    public boolean isKeepOldOwnerAsMember() {
        return keepOldOwnerAsMember;
    }

    public void setKeepOldOwnerAsMember(boolean keepOldOwnerAsMember) {
        this.keepOldOwnerAsMember = keepOldOwnerAsMember;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
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
