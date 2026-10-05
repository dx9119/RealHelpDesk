package com.ukhanov.realhelpdesk.feature.ticketmanager.service;

import java.io.UnsupportedEncodingException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import jakarta.mail.MessagingException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.ukhanov.realhelpdesk.core.mail.model.EmailTemplates;
import com.ukhanov.realhelpdesk.core.mail.model.NotificationEvent;
import com.ukhanov.realhelpdesk.core.mail.service.EmailDeliveryService;
import com.ukhanov.realhelpdesk.core.pagination.dto.PageResponse;
import com.ukhanov.realhelpdesk.core.pagination.service.PaginationAdapter;
import com.ukhanov.realhelpdesk.core.security.accesscontrol.TicketAccessValidationService;
import com.ukhanov.realhelpdesk.core.security.user.CurrentUserProvider;
import com.ukhanov.realhelpdesk.core.security.user.model.UserModel;
import com.ukhanov.realhelpdesk.domain.portal.model.PortalModel;
import com.ukhanov.realhelpdesk.domain.portal.service.PortalDomainService;
import com.ukhanov.realhelpdesk.domain.ticket.model.TicketLiveStatus;
import com.ukhanov.realhelpdesk.domain.ticket.model.TicketModel;
import com.ukhanov.realhelpdesk.domain.ticket.model.TicketPriority;
import com.ukhanov.realhelpdesk.domain.ticket.model.TicketStatus;
import com.ukhanov.realhelpdesk.domain.ticket.repository.TicketRepository;
import com.ukhanov.realhelpdesk.domain.ticket.service.TicketDomainService;
import com.ukhanov.realhelpdesk.feature.notificationmanager.service.NotificationPublisher;
import com.ukhanov.realhelpdesk.feature.portalmanager.exception.PortalException;
import com.ukhanov.realhelpdesk.feature.ticketmanager.dto.CreateTicketRequest;
import com.ukhanov.realhelpdesk.feature.ticketmanager.dto.CreateTicketResponse;
import com.ukhanov.realhelpdesk.feature.ticketmanager.dto.TicketResponseOld;
import com.ukhanov.realhelpdesk.feature.ticketmanager.exception.TicketException;
import com.ukhanov.realhelpdesk.feature.ticketmanager.mapper.TicketMapper;

@Service
public class TicketManageService {
    private static final Logger logger = LoggerFactory.getLogger(TicketManageService.class);

    private static final Set<String> SORTABLE_TICKET_FIELDS = Set.of("createdAt", "title", "ticketStatus", "ticketPriority");

    private final TicketDomainService ticketDomainService;
    private final CurrentUserProvider currentUserProvider;
    private final PortalDomainService portalDomainService;
    private final PaginationAdapter paginationAdapter;
    private final EmailDeliveryService emailDeliveryService;
    private final TicketAccessValidationService ticketAccessValidationService;
    private final EmailTemplates emailTemplates;
    private final NotificationPublisher notificationPublisher;

    private final TicketRepository ticketRepository;

    public TicketManageService(TicketDomainService ticketDomainService, CurrentUserProvider currentUserProvider,
            PortalDomainService portalDomainService, PaginationAdapter paginationAdapter, EmailDeliveryService emailDeliveryService,
            TicketAccessValidationService ticketAccessValidationService, TicketRepository ticketRepository, EmailTemplates emailTemplates,
            NotificationPublisher notificationPublisher) {
        this.ticketDomainService = ticketDomainService;
        this.currentUserProvider = currentUserProvider;
        this.portalDomainService = portalDomainService;
        this.paginationAdapter = paginationAdapter;
        this.emailDeliveryService = emailDeliveryService;
        this.ticketAccessValidationService = ticketAccessValidationService;
        this.ticketRepository = ticketRepository;
        this.emailTemplates = emailTemplates;
        this.notificationPublisher = notificationPublisher;
    }

    public TicketResponseOld getTicketById(Long ticketId) throws TicketException {
        Objects.requireNonNull(ticketId, "ticketId не должен быть null");

        TicketModel ticket = ticketDomainService.findTicketById(ticketId);

        return TicketMapper.toResponse(ticket);
    }

    public CreateTicketResponse createTicket(CreateTicketRequest request, Long portalId)
            throws PortalException, MessagingException, UnsupportedEncodingException {
        Objects.requireNonNull(request, "CreateTicketRequest не должен быть null");
        Objects.requireNonNull(portalId, "portalId не должен быть null");

        UserModel user = currentUserProvider.getCurrentUserModel();

        PortalModel portal = portalDomainService.getPortalById(portalId);

        TicketModel ticket = TicketMapper.fromRequest(request, user, portal);
        TicketModel saved = ticketDomainService.saveTicket(ticket);
        logger.info("Создана заявка {} в портале {}", saved.getId(), portalId);

        // In-app оповещение о новой заявке: до письма, чтобы не зависеть от SMTP; автора исключает publisher
        notificationPublisher.publishToPortalUsers(portal, NotificationEvent.NEW_TICKET, user.getId(), saved.getId(), saved.getTitle());

        // Отправляем письмо с оповещением о создании заявки всем пользователям портала
        emailDeliveryService.initNotifyPortalUsers(portal, emailTemplates.ticketCreatedSubject(ticket.getId()),
                emailTemplates.ticketCreatedBody(ticket.getId(), portal.getId()), NotificationEvent.NEW_TICKET);

        return new CreateTicketResponse(saved.getId());

    }

    public List<TicketResponseOld> getAllTickets(Long portalId) {
        Objects.requireNonNull(portalId, "portalId не должен быть null");

        List<TicketModel> tickets = ticketDomainService.getTicketsByPortalId(portalId);

        return tickets.stream().map(TicketMapper::toResponse).toList();
    }

    public PageResponse<TicketResponseOld> getPageTickets(Long portalId, int page, int size, String sortBy, String order)
            throws TicketException, PortalException {

        Objects.requireNonNull(portalId, "portalId не должен быть null");

        PageRequest pageRequest = paginationAdapter.buildPageRequest(page, size, sortBy, order, SORTABLE_TICKET_FIELDS);
        Page<TicketModel> ticketPage = ticketDomainService.getTicketsPageByPortalId(portalId, pageRequest);

        Page<TicketResponseOld> mappedPage = ticketPage.map(TicketMapper::toResponse);
        return paginationAdapter.mapToResponse(mappedPage, sortBy, order);
    }

    public PageResponse<TicketResponseOld> getPageTicketsByAutor(int page, int size, String sortBy, String order)
            throws TicketException, PortalException {

        PageRequest pageRequest = paginationAdapter.buildPageRequest(page, size, sortBy, order, SORTABLE_TICKET_FIELDS);
        UserModel user = currentUserProvider.getCurrentUserModel();

        Page<TicketModel> ticketPage = ticketDomainService.getTicketsPageByUserId(user.getId(), pageRequest);

        Page<TicketResponseOld> mappedPage = ticketPage.map(TicketMapper::toResponse);
        return paginationAdapter.mapToResponse(mappedPage, sortBy, order);
    }

    public PageResponse<TicketResponseOld> getPageTicketsByIds(Set<Long> ids, int page, int size, String sortBy, String order)
            throws TicketException, PortalException {
        Objects.requireNonNull(ids, "ids не должен быть null");

        PageRequest pageRequest = paginationAdapter.buildPageRequest(page, size, sortBy, order, SORTABLE_TICKET_FIELDS);
        Page<TicketModel> ticketPage = ticketDomainService.getTicketsByIds(ids, pageRequest);

        Page<TicketResponseOld> mappedPage = ticketPage.map(TicketMapper::toResponse);
        return paginationAdapter.mapToResponse(mappedPage, sortBy, order);
    }

    public Set<Long> getIdTicketNoAnswer(Long portalId) {
        Objects.requireNonNull(portalId, "portalId не должен быть null");

        Set<Long> ticketIds = ticketDomainService.getIdTicketWithNoAnswer(portalId);

        return ticketIds;
    }

    public Set<Long> getIdTicketWithStatus(Long portalId, TicketStatus status) {
        Objects.requireNonNull(portalId, "portalId не должен быть null");
        Objects.requireNonNull(status, "status не должен быть null");

        return ticketDomainService.getIdTicketWithStatus(portalId, status);
    }

    public void setTicketStatus(Long portalId, Long ticketId, TicketStatus status)
            throws TicketException, PortalException, MessagingException, UnsupportedEncodingException {

        Objects.requireNonNull(portalId, "portalId не должен быть null");
        Objects.requireNonNull(ticketId, "ticketId не должен быть null");
        Objects.requireNonNull(status, "status не должен быть null");

        TicketModel ticket = ticketDomainService.findTicketById(ticketId);

        if (!ticketAccessValidationService.hasTicketChange(portalId, ticketId)) {
            throw new TicketException("You can't change status of ticket");
        }

        TicketStatus previousStatus = ticket.getTicketStatus();
        ticket.setTicketStatus(status);
        TicketModel ticketSaved = ticketDomainService.saveTicket(ticket);
        logger.info("Заявка {}: статус {} → {}", ticketId, previousStatus, status);

        PortalModel portal = portalDomainService.getPortalById(portalId);

        // In-app оповещение об изменении заявки (действующего пользователя publisher исключает)
        notificationPublisher.publishToPortalUsers(portal, NotificationEvent.CHANGE_TICKET, currentUserProvider.getCurrentUserId(),
                ticketSaved.getId(), ticketSaved.getTitle());

        // Отправляем письмо
        emailDeliveryService.initNotifyPortalUsers(portal, emailTemplates.updateStatusTicketSubject(ticketSaved.getId(), status),
                emailTemplates.updateStatusTicketBody(ticketSaved.getId(), portalId), NotificationEvent.CHANGE_TICKET);
    }

    public void setTicketPriority(Long portalId, Long ticketId, TicketPriority priority)
            throws TicketException, PortalException, MessagingException, UnsupportedEncodingException {

        Objects.requireNonNull(portalId, "portalId не должен быть null");
        Objects.requireNonNull(ticketId, "ticketId не должен быть null");
        Objects.requireNonNull(priority, "priority не должен быть null");

        TicketModel ticket = ticketDomainService.findTicketById(ticketId);

        if (!ticketAccessValidationService.hasTicketChange(portalId, ticketId)) {
            throw new TicketException("Вы не можете изменять приоритет данной заявки");
        }

        TicketPriority previousPriority = ticket.getTicketPriority();
        ticket.setTicketPriority(priority);
        TicketModel ticketSaved = ticketDomainService.saveTicket(ticket);
        logger.info("Заявка {}: приоритет {} → {}", ticketId, previousPriority, priority);
        PortalModel portal = portalDomainService.getPortalById(portalId);

        // In-app оповещение об изменении заявки
        notificationPublisher.publishToPortalUsers(portal, NotificationEvent.CHANGE_TICKET, currentUserProvider.getCurrentUserId(),
                ticketSaved.getId(), ticketSaved.getTitle());

        // Отправляем письмо
        emailDeliveryService.initNotifyPortalUsers(portal, emailTemplates.updatePriorityTicketSubject(ticketSaved.getId(), priority),
                emailTemplates.updatePriorityTicketBody(ticketSaved.getId(), portal.getId()), NotificationEvent.CHANGE_TICKET);
    }

    public void deleteTicket(Long ticketID, Long portalId)
            throws TicketException, PortalException, MessagingException, UnsupportedEncodingException {
        Objects.requireNonNull(ticketID, "ticketID не должен быть null");
        Objects.requireNonNull(portalId, "portalId не должен быть null");

        if (!ticketAccessValidationService.hasTicketChange(portalId, ticketID)) {
            throw new TicketException("Вы не можете удалять данную заявку");
        }

        UserModel user = currentUserProvider.getCurrentUserModel();
        TicketModel ticket = ticketDomainService.findTicketById(ticketID);
        ticket.setWhoDelete(user.getId());
        ticket.setTimeDelete(Instant.now());

        ticket.setTicketLiveStatus(TicketLiveStatus.DELETE);
        TicketModel ticketSaved = ticketRepository.save(ticket);
        logger.info("Заявка {} удалена пользователем {}", ticketSaved.getId(), user.getId());

        PortalModel portal = portalDomainService.getPortalById(portalId);

        // In-app оповещение об удалении заявки
        notificationPublisher.publishToPortalUsers(portal, NotificationEvent.TICKET_DELETED, user.getId(), ticketSaved.getId(),
                ticketSaved.getTitle());

        // Отправляем письмо
        emailDeliveryService.initNotifyPortalUsers(portal, emailTemplates.deletedTicketSubject(ticketID),
                emailTemplates.deletedTicketBody(ticketID, user.getEmail()), NotificationEvent.TICKET_DELETED);
    }

}
