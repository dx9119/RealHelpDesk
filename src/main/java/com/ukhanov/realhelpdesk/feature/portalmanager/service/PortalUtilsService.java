package com.ukhanov.realhelpdesk.feature.portalmanager.service;

import com.ukhanov.realhelpdesk.feature.portalmanager.exception.PortalException;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PortalUtilsService {

  private static final Logger logger = LoggerFactory.getLogger(PortalUtilsService.class);

  public boolean isValidUUIDFormat(String uuidStr) {
    if (uuidStr == null) {
      return false;
    }
    try {
      UUID.fromString(uuidStr); // Проверка формата через парсинг
      return true;
    } catch (IllegalArgumentException e) {
      return false;
    }
  }

  public void validateUUIDList(Set<UUID> userIds) throws PortalException {
    for (UUID userId : userIds) {
      if (!isValidUUIDFormat(userId.toString())) {
        logger.info("format UUID is not valid: {}", userId);
        throw new PortalException("Неверный формат UUID: " + userId);
      }
    }
  }
}
