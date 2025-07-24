package com.ukhanov.realhelpdesk.domain.portal.service;

import com.ukhanov.realhelpdesk.domain.portal.model.PortalModel;
import com.ukhanov.realhelpdesk.domain.portal.repository.PortalRepository;

import com.ukhanov.realhelpdesk.feature.portalmanager.exception.PortalException;
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
        this.portalRepository = Objects.requireNonNull(portalRepository, "portalRepository must not be null");
    }

    public List<PortalModel> getPortalsByOwnerId(UUID ownerId) {
        Objects.requireNonNull(ownerId, "ownerId must not be null");
        logger.debug("Fetching all portals by ownerId: {}", ownerId);
        return portalRepository.findAllByOwnerIdOrderByCreatedAtDesc(ownerId);
    }

    public Page<PortalModel> getPortalsPageByOwnerId(UUID ownerId, Pageable pageable) {
        Objects.requireNonNull(ownerId, "ownerId must not be null");
        logger.debug("Fetching paged portals by ownerId: {}", ownerId);
        return portalRepository.findAllByOwnerIdOrderByCreatedAtDesc(ownerId, pageable);
    }

    public PortalModel getPortalById(Long portalId) throws PortalException {
        Objects.requireNonNull(portalId, "portalId must not be null");
        logger.debug("Fetching portal by ID: {}", portalId);
        return portalRepository.findById(portalId)
            .orElseThrow(() -> new PortalException("Портал с ID " + portalId + " не найден"));
    }

    public PortalModel savePortal(PortalModel portal) {
        Objects.requireNonNull(portal, "portal must not be null");
        logger.info("Saving portal: {}", portal);
        return portalRepository.save(portal);
    }

    public boolean isPortalExistByName (String portalName) {
        Objects.requireNonNull(portalName, "portalName must not be null");
        return portalRepository.existsByName(portalName);
    }

    public Page<PortalModel> getPortalPageAccessByUser(UUID userId, Pageable pageable) {
        Objects.requireNonNull(userId, "userId must not be null");
        logger.debug("Fetching paged accessible portals for userId: {}", userId);
        return portalRepository.findAccessibleByUserId(userId, pageable);
    }

    public boolean deletePortalById(Long portalId) {
        Objects.requireNonNull(portalId, "portalId must not be null");
        logger.info("Deleting portal by ID: {}", portalId);
        portalRepository.deleteById(portalId);
        return true;
    }

    public List<PortalModel> getAllSharedPortals(UUID userId) {
        Objects.requireNonNull(userId, "userId must not be null");
        logger.info("Fetching all accessible portals for userId: {}", userId);
        return portalRepository.findAllAccessibleByUserId(userId);
    }
}
