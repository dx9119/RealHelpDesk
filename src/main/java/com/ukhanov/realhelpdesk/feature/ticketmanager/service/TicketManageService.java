package com.ukhanov.realhelpdesk.feature.ticketmanager.service;

import com.ukhanov.realhelpdesk.core.mail.dto.TicketCreatedNotificationDto;
import com.ukhanov.realhelpdesk.core.mail.model.NotificationEvent;
import com.ukhanov.realhelpdesk.core.mail.service.EmailDeliveryService;
import com.ukhanov.realhelpdesk.core.security.accesscontrol.AccessValidationService;
import com.ukhanov.realhelpdesk.core.security.user.CurrentUserProvider;
import com.ukhanov.realhelpdesk.core.security.user.model.UserModel;
import com.ukhanov.realhelpdesk.domain.portal.model.PortalModel;
import com.ukhanov.realhelpdesk.domain.portal.service.PortalDomainService;
import com.ukhanov.realhelpdesk.domain.ticket.model.TicketModel;
import com.ukhanov.realhelpdesk.domain.ticket.model.TicketPriority;
import com.ukhanov.realhelpdesk.domain.ticket.model.TicketStatus;
import com.ukhanov.realhelpdesk.domain.ticket.service.TicketDomainService;
import com.ukhanov.realhelpdesk.core.pagination.dto.PageResponse;
import com.ukhanov.realhelpdesk.core.pagination.service.PaginationService;
import com.ukhanov.realhelpdesk.feature.portalmanager.exception.PortalException;
import com.ukhanov.realhelpdesk.feature.portalmanager.service.PortalManageService;
import com.ukhanov.realhelpdesk.feature.ticketmanager.dto.CreateTicketRequest;
import com.ukhanov.realhelpdesk.feature.ticketmanager.dto.CreateTicketResponse;
import com.ukhanov.realhelpdesk.feature.ticketmanager.dto.TicketResponse;
import com.ukhanov.realhelpdesk.feature.ticketmanager.exception.TicketException;
import com.ukhanov.realhelpdesk.feature.ticketmanager.mapper.TicketMapper;
import jakarta.mail.MessagingException;
import java.time.Instant;
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
    private final PortalManageService portalManageService;
    private final PaginationService paginationService;
    private final EmailDeliveryService emailDeliveryService;
    private final AccessValidationService accessValidationService;

    public TicketManageService(TicketDomainService ticketDomainService,
                               CurrentUserProvider currentUserProvider,
                               PortalDomainService portalDomainService,
        PortalManageService portalManageService,
        PaginationService paginationService, EmailDeliveryService emailDeliveryService,
        AccessValidationService accessValidationService) {
        this.ticketDomainService = ticketDomainService;
        this.currentUserProvider = currentUserProvider;
        this.portalDomainService = portalDomainService;
      this.portalManageService = portalManageService;
      this.paginationService = paginationService;
      this.emailDeliveryService = emailDeliveryService;
      this.accessValidationService = accessValidationService;
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
        logger.info("Ticket created successfully with ID: {}", saved.getId());

        // Отправляем письмо с оповещением о создании заявки всем пользователям портала
        TicketCreatedNotificationDto notify = new TicketCreatedNotificationDto.Builder()
            .info(portalId,saved.getId())
            .build();

        emailDeliveryService.initNotifyPortalUsers(
            portal,
            notify.getSubject(),
            notify.getMessage(),
            NotificationEvent.NEW_TICKET
        );

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

    public PageResponse<TicketResponse> getPageTicketsByFilters(
        int page,
        int size,
        String sortBy,
        String order,
        String search,
        Instant startDate,
        Instant endDate,
        TicketStatus ticketStatus,
        TicketPriority ticketPriority
    ) throws TicketException, PortalException {

        PageRequest pageRequest = paginationService.buildPageRequest(page, size, sortBy, order);
        List<Long> portalIds = portalManageService.mapAccessiblePortalsToIds();
        if (portalIds.isEmpty()) {
            throw new TicketException("You don't have access to any portals");
        }

        Page<TicketModel> ticketPage = ticketDomainService.getTicketsPageByPortalsAndFilters(
            portalIds, search, startDate, endDate, ticketStatus, ticketPriority, pageRequest
        );
        logger.debug("Finding tickets for portals: {} with status={} and priority={}", portalIds, ticketStatus, ticketPriority);

        Page<TicketResponse> mappedPage = ticketPage.map(TicketMapper::toResponse);
        return paginationService.mapToResponse(mappedPage, sortBy, order);
    }



    public PageResponse<TicketResponse> getPageTicketsByIds(Set<Long> ids, int page, int size, String sortBy, String order)
        throws TicketException, PortalException {
        Objects.requireNonNull(ids, "ids must not be null");

        PageRequest pageRequest = paginationService.buildPageRequest(page, size, sortBy, order);
        Page<TicketModel> ticketPage = ticketDomainService.getTicketsByIds(ids, pageRequest);
        Page<TicketResponse> mappedPage = ticketPage.map(TicketMapper::toResponse);

        return paginationService.mapToResponse(mappedPage, sortBy, order);
    }



    public Set<Long> getIdTicketNoAnswer(Long portalId) {
        Objects.requireNonNull(portalId, "portalId must not be null");
        return ticketDomainService.getIdTicketWithNoAnswer(portalId);
    }

    public Set<Long> getIdTicketWithStatus(Long portalId, TicketStatus status) {
        Objects.requireNonNull(status, "status must not be null");
        return ticketDomainService.getIdTicketWithStatus(portalId, status);
    }

    public void setTicketStatus(Long portalId, Long ticketId, TicketStatus status)
        throws TicketException, PortalException, MessagingException {
       Objects.requireNonNull(ticketId, "ticketId must not be null");
       Objects.requireNonNull(status, "status must not be null");

       UserModel user = currentUserProvider.getCurrentUserModel();
       TicketModel ticket = ticketDomainService.findTicketById(ticketId);

       boolean isAccessToPortal = accessValidationService.hasPortalAccess(portalId);
       boolean isAuthorOfTicket = ticket.getAuthor().getId().equals(user.getId());

       if (!(isAuthorOfTicket || isAccessToPortal)) {
           throw new TicketException("You can't change status of ticket");
       }

       ticket.setTicketStatus(status);
       ticketDomainService.saveTicket(ticket);

       PortalModel portal = portalDomainService.getPortalById(portalId);
       emailDeliveryService.initNotifyPortalUsers(
            portal,
            "Обновление статуса заявки #" + ticketId +"("+status+")",
            "Для заявки #" + ticketId + " был изменен статус на " + status,
            NotificationEvent.CHANGE_TICKET);

       logger.debug("ticket status updated");
    }

    public void setTicketPriority(Long portalId, Long ticketId, TicketPriority priority)
        throws TicketException, PortalException, MessagingException {
        Objects.requireNonNull(ticketId, "ticketId must not be null");
        Objects.requireNonNull(priority, "priority must not be null");

        UserModel user = currentUserProvider.getCurrentUserModel();
        TicketModel ticket = ticketDomainService.findTicketById(ticketId);

        boolean isAccessToPortal = accessValidationService.hasPortalAccess(portalId);
        boolean isAuthorOfTicket = ticket.getAuthor().getId().equals(user.getId());

        if (!(isAuthorOfTicket || isAccessToPortal)) {
            throw new TicketException("You can't change priority of ticket");
        }

        ticket.setTicketPriority(priority);
        ticketDomainService.saveTicket(ticket);

        PortalModel portal = portalDomainService.getPortalById(portalId);
        emailDeliveryService.initNotifyPortalUsers(
            portal,
            "Обновление приоритета заявки #" + ticketId +"("+priority+")",
            "Для заявки #" + ticketId + " был изменен приоритет на " + priority,
            NotificationEvent.CHANGE_TICKET);
        logger.debug("ticket priority updated");
    }
}


















