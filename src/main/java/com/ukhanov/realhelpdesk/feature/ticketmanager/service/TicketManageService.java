package com.ukhanov.realhelpdesk.feature.ticketmanager.service;

import com.ukhanov.realhelpdesk.core.mail.dto.TicketCreatedNotificationDto;
import com.ukhanov.realhelpdesk.core.mail.model.NotificationEvent;
import com.ukhanov.realhelpdesk.core.mail.service.EmailDeliveryService;
import com.ukhanov.realhelpdesk.core.security.user.CurrentUserProvider;
import com.ukhanov.realhelpdesk.core.security.user.model.UserModel;
import com.ukhanov.realhelpdesk.domain.portal.model.PortalModel;
import com.ukhanov.realhelpdesk.domain.portal.service.PortalDomainService;
import com.ukhanov.realhelpdesk.domain.ticket.model.TicketModel;
import com.ukhanov.realhelpdesk.domain.ticket.service.TicketDomainService;
import com.ukhanov.realhelpdesk.feature.pagination.dto.PageResponse;
import com.ukhanov.realhelpdesk.feature.pagination.service.PaginationService;
import com.ukhanov.realhelpdesk.feature.portalmanager.exception.PortalException;
import com.ukhanov.realhelpdesk.feature.portalmanager.service.PortalManageService;
import com.ukhanov.realhelpdesk.feature.ticketmanager.dto.CreateTicketRequest;
import com.ukhanov.realhelpdesk.feature.ticketmanager.dto.CreateTicketResponse;
import com.ukhanov.realhelpdesk.feature.ticketmanager.dto.TicketResponse;
import com.ukhanov.realhelpdesk.feature.ticketmanager.exception.TicketException;
import com.ukhanov.realhelpdesk.feature.ticketmanager.mapper.TicketMapper;
import jakarta.mail.MessagingException;
import java.util.Set;
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
    private final EmailDeliveryService emailDeliveryService;

    public TicketManageService(TicketDomainService ticketDomainService,
                               CurrentUserProvider currentUserProvider,
                               PortalDomainService portalDomainService,
        PaginationService paginationService,
        PortalManageService portalManageService, EmailDeliveryService emailDeliveryService) {
        this.ticketDomainService = ticketDomainService;
        this.currentUserProvider = currentUserProvider;
        this.portalDomainService = portalDomainService;
      this.paginationService = paginationService;
      this.emailDeliveryService = emailDeliveryService;
    }

    public TicketResponse getTicketById(Long ticketId) {
        Objects.requireNonNull(ticketId, "ticketId must not be null");
        TicketModel ticket = ticketDomainService.findTicketById(ticketId);
        return TicketMapper.toResponse(ticket);
    }

    public CreateTicketResponse createTicket(CreateTicketRequest request, Long portalId)
        throws PortalException, MessagingException {
        Objects.requireNonNull(request, "CreateTicketRequest must not be null");
        Objects.requireNonNull(portalId, "portalId must not be null");

        logger.debug("Starting ticket creation. Request: {}, Portal ID: {}", request, portalId);

        UserModel user = currentUserProvider.getCurrentUserModel();
        logger.debug("Current user retrieved: {}", user.getId());

        PortalModel portal = portalDomainService.getPortalById(portalId);

        TicketModel ticket = TicketMapper.fromRequest(request, user, portal);
        TicketModel saved = ticketDomainService.saveTicket(ticket);

        // Отправляем письмо с оповещением о создании заявки
        TicketCreatedNotificationDto notify = new TicketCreatedNotificationDto.Builder()
            .info(portalId,saved.getId())
            .build();

        emailDeliveryService.sendUserNotification(
            user.getEmail(),
            notify.getSubject(),
            notify.getMessage(),
            NotificationEvent.NEW_TICKET
        );
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
        throws TicketException, PortalException {

        PageRequest pageRequest = paginationService.buildPageRequest(page, size, sortBy, order);
        Page<TicketModel> ticketPage = ticketDomainService.getTicketsPageByPortalId(portalId, pageRequest);
        Page<TicketResponse> mappedPage = ticketPage.map(TicketMapper::toResponse);

        return paginationService.mapToResponse(mappedPage, sortBy, order);
    }


    public PageResponse<TicketResponse> getPageTicketsByAutor(int page, int size, String sortBy, String order)
        throws TicketException, PortalException {
        PageRequest pageRequest = paginationService.buildPageRequest(page, size, sortBy, order);
        UserModel user = currentUserProvider.getCurrentUserModel();
        Page<TicketModel> ticketPage = ticketDomainService.getTicketsPageByUserId(user.getId(), pageRequest);
        Page<TicketResponse> mappedPage = ticketPage.map(TicketMapper::toResponse);
        return paginationService.mapToResponse(mappedPage, sortBy, order);
    }

    public Set<Long> getTicketNoAnswer(Long portalId) {
        Objects.requireNonNull(portalId, "portalId must not be null");
        return ticketDomainService.getIdTicketWithNoAnswer(portalId);
    }
}


















