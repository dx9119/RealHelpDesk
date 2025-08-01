package com.ukhanov.realhelpdesk.domain.ticket.service;

import com.ukhanov.realhelpdesk.domain.ticket.model.TicketModel;
import com.ukhanov.realhelpdesk.domain.ticket.model.TicketPriority;
import com.ukhanov.realhelpdesk.domain.ticket.model.TicketStatus;
import com.ukhanov.realhelpdesk.domain.ticket.repository.TicketRepository;
import com.ukhanov.realhelpdesk.domain.ticket.repository.TicketSpecification;
import jakarta.persistence.PersistenceException;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
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


    public TicketModel saveTicket(TicketModel ticketModel) {
        Objects.requireNonNull(ticketModel, "Ticket cannot be null!");

        try {
            TicketModel savedTicket = ticketRepository.save(ticketModel);
            logger.debug("Saved ticket: {}", savedTicket.getId());
            return savedTicket;
        } catch (Exception e) {
            logger.error("Failed to save ticket: {}", ticketModel, e);
            throw new PersistenceException("Could not save ticket", e);
        }
    }

    // Найти заявку по ID
    public TicketModel findTicketById(Long ticketId) {
        Objects.requireNonNull(ticketId, "Ticket ID cannot be null!");

        TicketModel ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> {
                    logger.warn("Ticket with ID {} not found!", ticketId);
                    return new PersistenceException("Ticket not found!");
                });

        logger.debug("Found ticket: {}", ticket);
        return ticket;
    }

    public List<TicketModel> getTicketsByPortalId(Long portalId) {
        Objects.requireNonNull(portalId, "portalId must not be null");
        logger.debug("Fetching all tickets for portal ID: {}", portalId);
        return ticketRepository.findAllByPortalId(portalId);
    }

    public Page<TicketModel> getTicketsPageByPortalId(Long portalId, Pageable pageable) {
        Objects.requireNonNull(portalId, "portalId must not be null");
        logger.debug("Fetching paged tickets by portalId: {}", portalId);
        return ticketRepository.findAllByPortalId(portalId, pageable);
    }

    public Page<TicketModel> getTicketsPageByUserId(UUID userId, Pageable pageable) {
        Objects.requireNonNull(userId, "userId must not be null");
        logger.debug("Fetching paged tickets by userId: {}", userId);
        return ticketRepository.findAllByAuthorId(userId, pageable);
    }

    public Page<TicketModel> getTicketsPageByPortalsAndFilters(
        List<Long> portalIds,
        String search,
        Instant startDate,
        Instant endDate,
        TicketStatus ticketStatus,
        TicketPriority ticketPriority,
        Pageable pageable
    ) {
        Specification<TicketModel> spec = TicketSpecification.withFilters(
            portalIds, search, startDate, endDate, ticketStatus, ticketPriority
        );
        return ticketRepository.findAll(spec, pageable);
    }




    public Page<TicketModel> getTicketsByIds(Set<Long> ids, Pageable pageable) {
        Objects.requireNonNull(ids, "ids must not be null");
        logger.debug("Fetching paged tickets by ids: {}", ids);

        return ticketRepository.findByIdIn(ids, pageable);
    }


    public Set<Long> getIdTicketWithNoAnswer(Long portalId) {
        Objects.requireNonNull(portalId, "portalId must not be null");
        logger.debug("Fetching all tickets with no answer for portal ID: {}", portalId);
        return ticketRepository.findOpenTicketIdsWithoutMessagesByPortalId(portalId);
    }

    public Set<Long> getIdTicketWithStatus(Long portalId, TicketStatus status) {
        Objects.requireNonNull(portalId, "portalId must not be null");
        logger.debug("Fetching all tickets with status {} for portal ID: {}", status, portalId);
        return ticketRepository.findTicketIdsByPortalIdAndStatus(portalId, status);
    }

}
