package com.ukhanov.realhelpdesk.core.security.accesscontrol;

import com.ukhanov.realhelpdesk.core.security.user.CurrentUserProvider;
import com.ukhanov.realhelpdesk.core.security.user.model.UserModel;
import com.ukhanov.realhelpdesk.domain.portal.model.PortalModel;
import com.ukhanov.realhelpdesk.domain.portal.service.PortalDomainService;
import com.ukhanov.realhelpdesk.domain.ticket.model.TicketAccessStatus;
import com.ukhanov.realhelpdesk.domain.ticket.model.TicketModel;
import com.ukhanov.realhelpdesk.domain.ticket.service.TicketDomainService;
import com.ukhanov.realhelpdesk.feature.portalmanager.exception.PortalException;
import com.ukhanov.realhelpdesk.feature.ticketmanager.exception.TicketException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Service
public class TicketAccessValidationService {
    private final PortalDomainService portalDomainService;
    private final CurrentUserProvider currentUserProvider;
    private final TicketDomainService ticketDomainService;

    private static final Logger logger = LoggerFactory.getLogger(TicketAccessValidationService.class);

    public TicketAccessValidationService(PortalDomainService portalDomainService,
                                         CurrentUserProvider currentUserProvider,
                                         TicketDomainService ticketDomainService) {
        this.portalDomainService = portalDomainService;
        this.currentUserProvider = currentUserProvider;
        this.ticketDomainService = ticketDomainService;
    }

    public boolean hasTicketAccess(Long portalId, Long ticketId) throws PortalException, TicketException {
        UUID currentUserId = currentUserProvider.getCurrentUserModel().getId();

        PortalModel portal = portalDomainService.getPortalById(portalId);
        TicketModel ticket = ticketDomainService.findTicketById(ticketId);

        if (!belongsToPortal(ticket, portalId)) {
            logger.info("Заявка {} не относится к порталу {}, доступ отклонён", ticketId, portalId);
            return false;
        }

        TicketAccessStatus status = ticket.getAccessStatus();

        if (status == TicketAccessStatus.ALL_USERS) {
            return true;
        }

        if (status == TicketAccessStatus.CREATOR_AND_PORTAL_USERS) {
            Set<UUID> allowedUsers = new HashSet<>(portal.getAllowedUserIds());
            allowedUsers.add(portal.getOwner().getId());
            allowedUsers.add(ticket.getAuthor().getId());
            return allowedUsers.contains(currentUserId);
        }

        logger.debug("Доступ отклонён: политика тикета не соответствует заданным условиям");
        return false;
    }

    public boolean hasTicketChange(Long portalId, Long ticketId) throws PortalException, TicketException {
        UserModel user = currentUserProvider.getCurrentUserModel();

        PortalModel portal = portalDomainService.getPortalById(portalId);
        TicketModel ticket = ticketDomainService.findTicketById(ticketId);

        if (!belongsToPortal(ticket, portalId)) {
            logger.info("Заявка {} не относится к порталу {}, изменение отклонено", ticketId, portalId);
            return false;
        }

        Set<UUID> allowedUsers = new HashSet<>(portal.getAllowedUserIds());
        allowedUsers.add(portal.getOwner().getId());
        allowedUsers.add(ticket.getAuthor().getId());
        UUID currentUserId = user.getId();

        //Изменить статус заявки может либо автор заявки, либо владельцы портала.
        return allowedUsers.contains(currentUserId);
    }

    // Заявка должна принадлежать тому порталу, от которого пришёл запрос,
    // иначе владелец случайного портала получил бы доступ к чужой заявке
    private boolean belongsToPortal(TicketModel ticket, Long portalId) {
        PortalModel ticketPortal = ticket.getPortal();
        return ticketPortal != null && portalId.equals(ticketPortal.getId());
    }

}
