package com.ukhanov.realhelpdesk.domain.portal.service;

import com.ukhanov.realhelpdesk.domain.portal.model.PortalModel;
import com.ukhanov.realhelpdesk.domain.portal.repository.PortalRepository;

import com.ukhanov.realhelpdesk.domain.ticket.model.TicketLiveStatus;
import com.ukhanov.realhelpdesk.feature.portalmanager.exception.PortalException;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class PortalDomainService {

    private static final Logger logger = LoggerFactory.getLogger(PortalDomainService.class);

    private final PortalRepository portalRepository;

    public PortalDomainService(PortalRepository portalRepository) {
        this.portalRepository = Objects.requireNonNull(portalRepository, "portalRepository не должен быть null");
    }

    public List<PortalModel> getPortalsByOwnerId(UUID ownerId) {
        Objects.requireNonNull(ownerId, "ownerId не должен быть null");
        logger.debug("Получение всех порталов владельца с ID: {}", ownerId);
        return portalRepository.findAllByOwnerIdOrderByCreatedAtDesc(ownerId);
    }

    public Page<PortalModel> getPortalsPageByOwnerId(UUID ownerId, Pageable pageable) {
        Objects.requireNonNull(ownerId, "ownerId не должен быть null");
        logger.debug("Получение порталов владельца с ID: {} с пагинацией", ownerId);
        return portalRepository.findAllByOwnerIdOrderByCreatedAtDesc(ownerId, pageable);
    }

    public PortalModel getPortalById(Long portalId) throws PortalException {
        Objects.requireNonNull(portalId, "portalId must not be null");
        logger.debug("Получен портал ID: {}", portalId);
        return portalRepository.findById(portalId)
                .orElseThrow(() -> new PortalException("Портал с ID " + portalId + " не найден"));
    }

    @Transactional
    public PortalModel savePortal(PortalModel portal) {
        Objects.requireNonNull(portal, "portal не должен быть null");
        logger.info("Сохранение портала: {}", portal);
        return portalRepository.save(portal);
    }

    public boolean isPortalExistByName(UUID ownerId, String portalName) {
        Objects.requireNonNull(portalName, "portalName не должен быть null");
        return portalRepository.existsByOwnerIdAndNameAndIsDeletedFalse(ownerId, portalName);
    }

    public Page<PortalModel> getPortalPageAccessByUser(UUID userId, Pageable pageable) {
        Objects.requireNonNull(userId, "userId не должен быть null");
        logger.debug("Получение доступных порталов для пользователя с ID: {} с пагинацией", userId);
        return portalRepository.findAccessibleByUserId(userId, pageable);
    }

    @Transactional
    public boolean deletePortalById(Long portalId) {
        Objects.requireNonNull(portalId, "portalId не должен быть null");
        logger.info("Удаление портала по ID: {}", portalId);
        portalRepository.deleteById(portalId);
        return true;
    }

    public List<PortalModel> getAllSharedPortals(UUID userId) {
        Objects.requireNonNull(userId, "userId не должен быть null");
        logger.info("Получение всех доступных порталов для пользователя с ID: {}", userId);
        return portalRepository.findAllAccessibleByUserId(userId);
    }

    public List<Long> getPublicPortalsByUserActivity(UUID userId){
        Objects.requireNonNull(userId, "userId не должен быть null");
        logger.info("Получение публичных порталов по активности пользователя с ID: {}", userId);
        return portalRepository.findPublicPortalIdsWithUserTickets(userId);
    }
}
