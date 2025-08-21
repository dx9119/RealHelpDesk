package com.ukhanov.realhelpdesk.domain.ticket.service;

import com.ukhanov.realhelpdesk.domain.ticket.model.TicketLiveStatus;
import com.ukhanov.realhelpdesk.domain.ticket.model.TicketModel;
import com.ukhanov.realhelpdesk.domain.ticket.model.TicketStatus;
import com.ukhanov.realhelpdesk.domain.ticket.repository.TicketRepository;
import com.ukhanov.realhelpdesk.feature.ticketmanager.exception.TicketException;
import jakarta.persistence.PersistenceException;
import java.util.Set;
import java.util.UUID;

import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public class TicketDomainService {

    private static final Logger logger = LoggerFactory.getLogger(TicketDomainService.class);

    private final TicketRepository ticketRepository;

    public TicketDomainService(TicketRepository ticketRepository) {
        this.ticketRepository = ticketRepository;
    }

    @Transactional
    public TicketModel saveTicket(TicketModel ticketModel) {
        Objects.requireNonNull(ticketModel, "Заявка не должна быть null!");

        try {
            TicketModel savedTicket = ticketRepository.save(ticketModel);
            logger.debug("Заявка сохранена: {}", savedTicket.getId());
            return savedTicket;
        } catch (Exception e) {
            logger.error("Не удалось сохранить заявку: {}", ticketModel, e);
            throw new PersistenceException("Не удалось сохранить заявку", e);
        }
    }

    // Найти заявку по ID
    public TicketModel findTicketById(Long ticketId) throws TicketException {
        Objects.requireNonNull(ticketId, "ID заявки не должен быть null!");

        TicketModel ticket = ticketRepository.findByIdAndTicketLiveStatus(ticketId, TicketLiveStatus.ACTIVE)
                .orElseThrow(() -> {
                    logger.warn("Заявка с ID {} не найдена!", ticketId);
                    return new TicketException("Заявка не найдена!");
                });

        return ticket;
    }

    public List<TicketModel> getTicketsByPortalId(Long portalId) {
        Objects.requireNonNull(portalId, "portalId не должен быть null");
        logger.debug("Получение всех заявок для портала с ID: {}", portalId);
        return ticketRepository.findAllByPortalIdAndTicketLiveStatus(portalId,TicketLiveStatus.ACTIVE);
    }

    public Page<TicketModel> getTicketsPageByPortalId(Long portalId, Pageable pageable) {
        Objects.requireNonNull(portalId, "portalId не должен быть null");
        logger.debug("Получение заявок с пагинацией для портала с ID: {}", portalId);
        return ticketRepository.findAllByPortalIdAndTicketLiveStatus(portalId, pageable, TicketLiveStatus.ACTIVE);
    }

    public Page<TicketModel> getTicketsPageByUserId(UUID userId, Pageable pageable) {
        Objects.requireNonNull(userId, "userId не должен быть null");
        logger.debug("Получение заявок с пагинацией для пользователя с ID: {}", userId);
        return ticketRepository.findAllByAuthorIdAndTicketLiveStatus(userId, TicketLiveStatus.ACTIVE, pageable);
    }

    public Page<TicketModel> getTicketsByIds(Set<Long> ids, Pageable pageable) {
        Objects.requireNonNull(ids, "ids не должны быть null");
        logger.debug("Получение заявок с пагинацией по ID: {}", ids);
        return ticketRepository.findByIdInAndTicketLiveStatus(ids,TicketLiveStatus.ACTIVE,pageable);
    }

    public Set<Long> getIdTicketWithNoAnswer(Long portalId) {
        Objects.requireNonNull(portalId, "portalId не должен быть null");
        logger.debug("Получение ID заявок без ответа для портала с ID: {}", portalId);
        return ticketRepository.findOpenTicketIdsWithoutMessagesByPortalIdAndLiveStatus(portalId,TicketLiveStatus.ACTIVE);
    }

    public Set<Long> getIdTicketWithStatus(Long portalId, TicketStatus status) {
        Objects.requireNonNull(portalId, "portalId не должен быть null");
        logger.debug("Получение ID заявок со статусом {} для портала с ID: {}", status, portalId);
        return ticketRepository.findTicketIdsByPortalIdAndStatusAndLiveStatus(portalId, status, TicketLiveStatus.ACTIVE);
    }

}
