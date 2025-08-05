package com.ukhanov.realhelpdesk.core.security.accesscontrol;

import com.ukhanov.realhelpdesk.core.security.user.CurrentUserProvider;
import com.ukhanov.realhelpdesk.domain.portal.model.PortalModel;
import com.ukhanov.realhelpdesk.domain.portal.service.PortalDomainService;
import com.ukhanov.realhelpdesk.domain.ticket.model.TicketAccessStatus;
import com.ukhanov.realhelpdesk.domain.ticket.model.TicketModel;
import com.ukhanov.realhelpdesk.domain.ticket.service.TicketDomainService;
import com.ukhanov.realhelpdesk.feature.portalmanager.exception.PortalException;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Service
public class TicketAccessValidationService {
    private final PortalDomainService portalDomainService;
    private final CurrentUserProvider currentUserProvider;
    private final TicketDomainService ticketDomainService;

    public TicketAccessValidationService(PortalDomainService portalDomainService, CurrentUserProvider currentUserProvider, TicketDomainService ticketDomainService) {
        this.portalDomainService = portalDomainService;
        this.currentUserProvider = currentUserProvider;
        this.ticketDomainService = ticketDomainService;
    }

    public boolean hasTicketAccess(Long portalId, Long ticketId) throws PortalException {
        UUID currentUserId = currentUserProvider.getCurrentUserModel().getId();

        PortalModel portal = portalDomainService.getPortalById(portalId);
        TicketModel ticket = ticketDomainService.findTicketById(ticketId);

        TicketAccessStatus status = ticket.getAccessStatus();

        if (status == TicketAccessStatus.ALL_USERS) {
            return true;
        }

        if (status == TicketAccessStatus.ACCESS_CREATOR_AND_PORTAL_USERS) {
            Set<UUID> allowedUsers = new HashSet<>(portal.getAllowedUserIds());
            allowedUsers.add(portal.getOwner().getId());
            allowedUsers.add(ticket.getAuthor().getId());

            return allowedUsers.contains(currentUserId);
        }

        return false;
    }
}
