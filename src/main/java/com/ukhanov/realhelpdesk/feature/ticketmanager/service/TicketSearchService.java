package com.ukhanov.realhelpdesk.feature.ticketmanager.service;

import com.ukhanov.realhelpdesk.core.security.user.CurrentUserProvider;
import com.ukhanov.realhelpdesk.domain.portal.model.PortalModel;
import com.ukhanov.realhelpdesk.domain.portal.repository.PortalRepository;
import com.ukhanov.realhelpdesk.domain.ticket.model.TicketModel;
import com.ukhanov.realhelpdesk.domain.ticket.model.TicketPriority;
import com.ukhanov.realhelpdesk.domain.ticket.model.TicketStatus;
import com.ukhanov.realhelpdesk.domain.ticket.repository.TicketRepository;
import com.ukhanov.realhelpdesk.domain.ticket.repository.TicketSpecification;
import com.ukhanov.realhelpdesk.feature.ticketmanager.dto.TicketResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;


@Service
@Transactional(readOnly = true)
public class TicketSearchService {

    private static final Logger logger = LoggerFactory.getLogger(TicketSearchService.class);

    private final TicketRepository ticketRepository;
    private final PortalRepository portalRepository;
    private final CurrentUserProvider currentUserProvider;

    public TicketSearchService(TicketRepository ticketRepository, PortalRepository portalRepository, CurrentUserProvider currentUserProvider) {
        this.ticketRepository = ticketRepository;
        this.portalRepository = portalRepository;
        this.currentUserProvider = currentUserProvider;
    }

    public Page<TicketResponse> searchTickets(
            String search, Instant startDate, Instant endDate, TicketStatus ticketStatus,
            TicketPriority ticketPriority, boolean isMyTickets, Pageable pageable) {

        UUID userId = currentUserProvider.getCurrentUserId();

        logger.debug("Запрос на поиск тикетов. Пользователь: {}, Поиск: '{}', Статус: {}, Приоритет: {}, Только мои: {}, Дата от: {}, Дата до: {}",
                userId, search, ticketStatus, ticketPriority, isMyTickets, startDate, endDate);

        Set<Long> accessiblePortalIds = getAccessiblePortalIds(userId);

        if (accessiblePortalIds.isEmpty()) {
            logger.debug("У пользователя {} нет доступа ни к одному порталу", userId);
            return Page.empty(pageable);
        }

        Specification<TicketModel> spec = TicketSpecification.build(
                search, startDate, endDate, ticketStatus, ticketPriority, isMyTickets, userId, accessiblePortalIds
        );

        return ticketRepository.findAll(spec, pageable).map(TicketResponse::new);
    }

    private Set<Long> getAccessiblePortalIds(UUID userId) {
        logger.debug("Получение доступных порталов для пользователя: {}", userId);

        List<PortalModel> ownedPortals = portalRepository.findAllByOwnerIdOrderByCreatedAtDesc(userId);
        List<PortalModel> allowedPortals = portalRepository.findAllAccessibleByUserId(userId);
        List<Long> publicPortalIds = portalRepository.findPublicPortalIdsWithUserTickets(userId);

        Set<Long> portalIds = new HashSet<>();
        ownedPortals.stream().map(PortalModel::getId).forEach(portalIds::add);
        allowedPortals.stream().map(PortalModel::getId).forEach(portalIds::add);
        portalIds.addAll(publicPortalIds);

        return portalIds;
    }
}
