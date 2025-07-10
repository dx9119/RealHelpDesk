package com.ukhanov.realhelpdesk.core.security.accesscontrol;

import com.ukhanov.realhelpdesk.core.security.user.CurrentUserProvider;
import com.ukhanov.realhelpdesk.domain.portal.model.PortalModel;
import com.ukhanov.realhelpdesk.domain.portal.service.PortalDomainService;
import com.ukhanov.realhelpdesk.feature.portalmanager.exception.PortalException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AccessValidationService {
  private final PortalDomainService portalDomainService;
  private final CurrentUserProvider currentUserProvider;

  private static final Logger logger = LoggerFactory.getLogger(AccessValidationService.class);

  public AccessValidationService(PortalDomainService portalDomainService,
      CurrentUserProvider currentUserProvider) {
    this.portalDomainService = portalDomainService;
    this.currentUserProvider = currentUserProvider;
  }

  public boolean hasPortalAccess(Long portalId) throws PortalException {
    PortalModel portal = portalDomainService.getPortalById(portalId);
    UUID currentUserId = currentUserProvider.getCurrentUserModel().getId();

    boolean isOwner = portal.getOwner().getId().equals(currentUserId);
    boolean isAllowedUser = portal.getAllowedUserIds().contains(currentUserId);

    if (!isOwner && !isAllowedUser) {
      logger.info("User {} does not have access to portal {}", currentUserId, portalId);
      return false;
    }

    return true;
  }


}
