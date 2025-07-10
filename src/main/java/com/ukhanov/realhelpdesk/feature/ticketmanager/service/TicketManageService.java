package com.ukhanov.realhelpdesk.feature.ticketmanager.service;

import com.ukhanov.realhelpdesk.core.security.user.CurrentUserProvider;
import com.ukhanov.realhelpdesk.core.security.user.model.UserModel;
import com.ukhanov.realhelpdesk.domain.portal.model.PortalModel;
import com.ukhanov.realhelpdesk.domain.portal.service.PortalDomainService;
import com.ukhanov.realhelpdesk.domain.ticket.model.TicketModel;
import com.ukhanov.realhelpdesk.domain.ticket.service.TicketDomainService;
import com.ukhanov.realhelpdesk.feature.pagination.dto.PageResponse;
import com.ukhanov.realhelpdesk.feature.pagination.service.PaginationService;
import com.ukhanov.realhelpdesk.feature.portalmanager.service.PortalManageService;
import com.ukhanov.realhelpdesk.feature.ticketmanager.dto.CreateTicketRequest;
import com.ukhanov.realhelpdesk.feature.ticketmanager.dto.CreateTicketResponse;
import com.ukhanov.realhelpdesk.feature.ticketmanager.dto.TicketResponse;
import com.ukhanov.realhelpdesk.feature.ticketmanager.exception.TicketException;
import com.ukhanov.realhelpdesk.feature.ticketmanager.mapper.TicketMapper;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public class TicketManageService {
    private static final Logger logger = LoggerFactory.getLogger(TicketManageService.class);

    private final TicketDomainService ticketDomainService;
    private final CurrentUserProvider currentUserProvider;
    private final PortalDomainService portalDomainService;
    private final PaginationService paginationService;

    public TicketManageService(TicketDomainService ticketDomainService,
                               CurrentUserProvider currentUserProvider,
                               PortalDomainService portalDomainService,
        PaginationService paginationService,
        PortalManageService portalManageService) {
        this.ticketDomainService = ticketDomainService;
        this.currentUserProvider = currentUserProvider;
        this.portalDomainService = portalDomainService;
      this.paginationService = paginationService;

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

    public PageResponse<TicketResponse> getPageTickets(Long portalId, int page, int size, String sortBy, String order)
        throws TicketException {
        logger.debug("Received request for paged tickets — portalId: {}, page: {}, size: {}, sortBy: {}, order: {}", portalId, page, size, sortBy, order);

        if(!validateAccessTicket(portalId)){
            throw new TicketException("Вам не разрешен доступ к данному порталу");
        }

        PageRequest pageRequest = paginationService.buildPageRequest(page, size, sortBy, order);
        Page<TicketModel> ticketPage = ticketDomainService.getTicketsPageByPortalId(portalId, pageRequest);
        Page<TicketResponse> mappedPage = ticketPage.map(TicketMapper::toResponse);

        return paginationService.mapToResponse(mappedPage, sortBy, order);
    }

    private boolean validateAccessTicket(Long portalId) {
        PortalModel portal = portalDomainService.getPortalById(portalId);
        Set<UUID> UserOfPortal = portal.getAllowedUserIds();
        UUID currentUserId = currentUserProvider.getCurrentUserModel().getId();

        if (!UserOfPortal.contains(currentUserId)) {
           return false;
        }
        return true;
    }


}


















