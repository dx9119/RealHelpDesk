package com.ukhanov.realhelpdesk.feature.ticketmanager.service;

import com.ukhanov.realhelpdesk.core.security.user.CurrentUserProvider;
import com.ukhanov.realhelpdesk.core.security.user.model.UserModel;
import com.ukhanov.realhelpdesk.domain.portal.model.PortalModel;
import com.ukhanov.realhelpdesk.domain.portal.service.PortalDomainService;
import com.ukhanov.realhelpdesk.domain.ticket.model.TicketModel;
import com.ukhanov.realhelpdesk.domain.ticket.service.TicketDomainService;
import com.ukhanov.realhelpdesk.feature.ticketmanager.dto.CreateTicketRequest;
import com.ukhanov.realhelpdesk.feature.ticketmanager.dto.CreateTicketResponse;
import com.ukhanov.realhelpdesk.feature.ticketmanager.dto.TicketResponse;
import com.ukhanov.realhelpdesk.feature.ticketmanager.mapper.TicketMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public class TicketManageService {
    private static final Logger logger = LoggerFactory.getLogger(TicketManageService.class);

    private final TicketDomainService ticketDomainService;
    private final CurrentUserProvider currentUserProvider;
    private final PortalDomainService portalDomainService;

    public TicketManageService(TicketDomainService ticketDomainService,
                               CurrentUserProvider currentUserProvider,
                               PortalDomainService portalDomainService) {
        this.ticketDomainService = ticketDomainService;
        this.currentUserProvider = currentUserProvider;
        this.portalDomainService = portalDomainService;
    }

    public CreateTicketResponse createTicket(CreateTicketRequest request, Long portalId) {
        Objects.requireNonNull(request, "CreateTicketRequest must not be null");
        Objects.requireNonNull(portalId, "portalId must not be null");

        logger.debug("Starting ticket creation. Request: {}, Portal ID: {}", request, portalId);

        UserModel user = currentUserProvider.getCurrentUserModel();
        logger.debug("Current user retrieved: {}", user.getId());

        PortalModel portal = portalDomainService.getPortalById(portalId);

        TicketModel ticket = TicketMapper.fromRequest(request, user, portal);
        TicketModel saved = ticketDomainService.saveTicket(ticket);

        logger.info("Ticket created successfully with ID: {}", saved.getId());

        return new CreateTicketResponse("Created ticket with ID: " + saved.getId());
    }

    public List<TicketResponse> getAllTickets(Long portalId) {
        Objects.requireNonNull(portalId, "portalId must not be null");
        logger.debug("Start retrieving all tickets for portal ID: {}", portalId);

        List<TicketModel> tickets = ticketDomainService.getTicketsByPortalId(portalId);
        logger.info("Found {} tickets for portal ID: {}", tickets.size(), portalId);

        return tickets.stream()
                .map(TicketMapper::toResponse)
                .toList();
    }


}


















