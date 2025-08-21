package com.ukhanov.realhelpdesk.feature.ticketmanager.service;

import com.ukhanov.realhelpdesk.core.mail.model.EmailTemplates;
import com.ukhanov.realhelpdesk.core.mail.model.NotificationEvent;
import com.ukhanov.realhelpdesk.core.mail.service.EmailDeliveryService;
import com.ukhanov.realhelpdesk.core.security.accesscontrol.AccessValidationService;
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
import com.ukhanov.realhelpdesk.core.pagination.dto.PageResponse;
import com.ukhanov.realhelpdesk.core.pagination.service.PaginationService;
import com.ukhanov.realhelpdesk.feature.portalmanager.exception.PortalException;
import com.ukhanov.realhelpdesk.feature.ticketmanager.dto.CreateTicketRequest;
import com.ukhanov.realhelpdesk.feature.ticketmanager.dto.CreateTicketResponse;
import com.ukhanov.realhelpdesk.feature.ticketmanager.dto.TicketResponseOld;
import com.ukhanov.realhelpdesk.feature.ticketmanager.exception.TicketException;
import com.ukhanov.realhelpdesk.feature.ticketmanager.mapper.TicketMapper;
import jakarta.mail.MessagingException;

import java.io.UnsupportedEncodingException;
import java.time.Instant;
import java.util.*;

import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
public class TicketManageService {
    private static final Logger logger = LoggerFactory.getLogger(TicketManageService.class);

    private final TicketDomainService ticketDomainService;
    private final CurrentUserProvider currentUserProvider;
    private final PortalDomainService portalDomainService;
    private final PaginationService paginationService;
    private final EmailDeliveryService emailDeliveryService;
    private final AccessValidationService accessValidationService;

    private final TicketRepository ticketRepository;

    public TicketManageService(TicketDomainService ticketDomainService,
                               CurrentUserProvider currentUserProvider,
                               PortalDomainService portalDomainService,
        PaginationService paginationService, EmailDeliveryService emailDeliveryService,
        AccessValidationService accessValidationService,
                               TicketRepository ticketRepository) {
        this.ticketDomainService = ticketDomainService;
        this.currentUserProvider = currentUserProvider;
        this.portalDomainService = portalDomainService;
      this.paginationService = paginationService;
      this.emailDeliveryService = emailDeliveryService;
      this.accessValidationService = accessValidationService;
        this.ticketRepository = ticketRepository;
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

        logger.debug("Начато создание тикета. Запрос: {}, ID портала: {}", request, portalId);

        UserModel user = currentUserProvider.getCurrentUserModel();

        PortalModel portal = portalDomainService.getPortalById(portalId);

        TicketModel ticket = TicketMapper.fromRequest(request, user, portal);
        TicketModel saved = ticketDomainService.saveTicket(ticket);


        // Отправляем письмо с оповещением о создании заявки всем пользователям портала
        emailDeliveryService.initNotifyPortalUsers(
            portal,
            EmailTemplates.ticketCreatedSubject(ticket.getId()),
            EmailTemplates.ticketCreatedBody(ticket.getId(), portal.getId()),
            NotificationEvent.NEW_TICKET
        );



        return new CreateTicketResponse("Тикет создан с ID: " + saved.getId());

    }

    public List<TicketResponseOld> getAllTickets(Long portalId) {
        Objects.requireNonNull(portalId, "portalId не должен быть null");
        logger.debug("Начато получение всех тикетов для портала с ID: {}", portalId);

        List<TicketModel> tickets = ticketDomainService.getTicketsByPortalId(portalId);

        return tickets.stream()
                .map(TicketMapper::toResponse)
                .toList();
    }

    public PageResponse<TicketResponseOld> getPageTickets(Long portalId, int page, int size, String sortBy, String order)
            throws TicketException, PortalException {

        Objects.requireNonNull(portalId, "portalId не должен быть null");
        logger.debug("Запрос на получение тикетов — портал ID: {}, страница: {}, размер: {}, сортировка: {}, порядок: {}", portalId, page, size, sortBy, order);

        PageRequest pageRequest = paginationService.buildPageRequest(page, size, sortBy, order);
        Page<TicketModel> ticketPage = ticketDomainService.getTicketsPageByPortalId(portalId, pageRequest);

        Page<TicketResponseOld> mappedPage = ticketPage.map(TicketMapper::toResponse);
        return paginationService.mapToResponse(mappedPage, sortBy, order);
    }



    public PageResponse<TicketResponseOld> getPageTicketsByAutor(int page, int size, String sortBy, String order)
            throws TicketException, PortalException {

        logger.debug("Запрос на получение тикетов по автору — страница: {}, размер: {}, сортировка: {}, порядок: {}", page, size, sortBy, order);

        PageRequest pageRequest = paginationService.buildPageRequest(page, size, sortBy, order);
        UserModel user = currentUserProvider.getCurrentUserModel();

        Page<TicketModel> ticketPage = ticketDomainService.getTicketsPageByUserId(user.getId(), pageRequest);

        Page<TicketResponseOld> mappedPage = ticketPage.map(TicketMapper::toResponse);
        return paginationService.mapToResponse(mappedPage, sortBy, order);
    }



    public PageResponse<TicketResponseOld> getPageTicketsByIds(Set<Long> ids, int page, int size, String sortBy, String order)
            throws TicketException, PortalException {
        Objects.requireNonNull(ids, "ids не должен быть null");
        logger.debug("Запрос на получение тикетов по ID — количество: {}, страница: {}, размер: {}, сортировка: {}, порядок: {}", ids.size(), page, size, sortBy, order);

        PageRequest pageRequest = paginationService.buildPageRequest(page, size, sortBy, order);
        Page<TicketModel> ticketPage = ticketDomainService.getTicketsByIds(ids, pageRequest);

        Page<TicketResponseOld> mappedPage = ticketPage.map(TicketMapper::toResponse);
        return paginationService.mapToResponse(mappedPage, sortBy, order);
    }


    public Set<Long> getIdTicketNoAnswer(Long portalId) {
        Objects.requireNonNull(portalId, "portalId не должен быть null");
        logger.debug("Запрос на получение ID тикетов без ответа для портала с ID: {}", portalId);

        Set<Long> ticketIds = ticketDomainService.getIdTicketWithNoAnswer(portalId);

        return ticketIds;
    }


    public Set<Long> getIdTicketWithStatus(Long portalId, TicketStatus status) {
        Objects.requireNonNull(portalId, "portalId не должен быть null");
        Objects.requireNonNull(status, "status не должен быть null");

        logger.debug("Запрос на получение ID тикетов со статусом '{}' для портала с ID: {}", status, portalId);

        return ticketDomainService.getIdTicketWithStatus(portalId, status);
    }


    public void setTicketStatus(Long portalId, Long ticketId, TicketStatus status)
            throws TicketException, PortalException, MessagingException, UnsupportedEncodingException {

        Objects.requireNonNull(portalId, "portalId не должен быть null");
        Objects.requireNonNull(ticketId, "ticketId не должен быть null");
        Objects.requireNonNull(status, "status не должен быть null");

        logger.debug("Запрос на обновление статуса тикета. Портал ID: {}, Тикет ID: {}, Новый статус: {}", portalId, ticketId, status);

        UserModel user = currentUserProvider.getCurrentUserModel();
        TicketModel ticket = ticketDomainService.findTicketById(ticketId);

        boolean isAccessToPortal = accessValidationService.hasPortalAccess(portalId);
        boolean isAuthorOfTicket = ticket.getAuthor().getId().equals(user.getId());

        if (!(isAuthorOfTicket || isAccessToPortal)) {
            throw new TicketException("You can't change status of ticket");
        }

        ticket.setTicketStatus(status);
        TicketModel ticketSaved = ticketDomainService.saveTicket(ticket);


        // Отправляем письмо
        emailDeliveryService.initNotifyPortalUsers(
                portalDomainService.getPortalById(portalId),
                EmailTemplates.updateStatusTicketSubject(ticketSaved.getId(),status),
                EmailTemplates.updateStatusTicketBody(ticketSaved.getId(),portalId),
                NotificationEvent.CHANGE_TICKET
        );
    }


    public void setTicketPriority(Long portalId, Long ticketId, TicketPriority priority)
            throws TicketException, PortalException, MessagingException, UnsupportedEncodingException {

        Objects.requireNonNull(portalId, "portalId не должен быть null");
        Objects.requireNonNull(ticketId, "ticketId не должен быть null");
        Objects.requireNonNull(priority, "priority не должен быть null");

        logger.debug("Запрос на обновление приоритета тикета. Портал ID: {}, Тикет ID: {}, Новый приоритет: {}", portalId, ticketId, priority);

        UserModel user = currentUserProvider.getCurrentUserModel();
        TicketModel ticket = ticketDomainService.findTicketById(ticketId);

        boolean isAccessToPortal = accessValidationService.hasPortalAccess(portalId);
        boolean isAuthorOfTicket = ticket.getAuthor().getId().equals(user.getId());

        if (!(isAuthorOfTicket || isAccessToPortal)) {
            throw new TicketException("Вы не можете изменять приоритет данной заявки");
        }

        ticket.setTicketPriority(priority);
        TicketModel ticketSaved = ticketDomainService.saveTicket(ticket);
        PortalModel portal = portalDomainService.getPortalById(portalId);

        // Отправляем письмо
        emailDeliveryService.initNotifyPortalUsers(
                portal,
                EmailTemplates.updatePriorityTicketSubject(ticketSaved.getId(),priority),
                EmailTemplates.updatePriorityTicketBody(ticketSaved.getId(),portal.getId()),
                NotificationEvent.CHANGE_TICKET
        );
    }


    public void deleteTicket (Long ticketID, Long portalId) throws TicketException, PortalException, MessagingException, UnsupportedEncodingException {
        Objects.requireNonNull(ticketID,"ticketID не должен быть null");
        Objects.requireNonNull(ticketID,"portalId не должен быть null");

        UserModel user = currentUserProvider.getCurrentUserModel();
        TicketModel ticket = ticketDomainService.findTicketById(ticketID);
        ticket.setWhoDelete(user.getId());
        ticket.setTimeDelete(Instant.now());

        ticket.setTicketLiveStatus(TicketLiveStatus.DELETE);
        TicketModel ticketSaved = ticketRepository.save(ticket);

        // Отправляем письмо
        emailDeliveryService.initNotifyPortalUsers(
                portalDomainService.getPortalById(portalId),
                EmailTemplates.deletedTicketSubject(ticketID),
                EmailTemplates.deletedTicketBody(ticketID,user.getEmail()),
                NotificationEvent.TICKET_DELETED
        );
    }

}


















