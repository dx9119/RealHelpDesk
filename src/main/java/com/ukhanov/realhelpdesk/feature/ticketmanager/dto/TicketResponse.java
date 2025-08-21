package com.ukhanov.realhelpdesk.feature.ticketmanager.dto;
import com.ukhanov.realhelpdesk.domain.ticket.model.TicketModel;
import java.time.Instant;


public class TicketResponse {

    private Long id;
    private String title;
    private String authorFullName;
    private String portalName;
    private Instant createdAt;
    private Long portalId;

    public TicketResponse() {
    }

    public TicketResponse(TicketModel ticket) {
        this.id = ticket.getId();
        this.title = ticket.getTitle();

        if (ticket.getAuthor() != null) {
            this.authorFullName = ticket.getAuthor().getFirstName();
        }
        if (ticket.getPortal() != null) {
            this.portalName = ticket.getPortal().getName();
            this.portalId = ticket.getPortal().getId();
        }
        this.createdAt = ticket.getCreatedAt();
    }

    public Long getPortalId() {
        return portalId;
    }
    public void setPortalId(Long portalId) {
        this.portalId = portalId;
    }
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getAuthorFullName() { return authorFullName; }
    public void setAuthorFullName(String authorFullName) { this.authorFullName = authorFullName; }
    public String getPortalName() { return portalName; }
    public void setPortalName(String portalName) { this.portalName = portalName; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
