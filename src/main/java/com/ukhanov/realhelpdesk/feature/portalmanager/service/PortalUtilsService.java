package com.ukhanov.realhelpdesk.feature.portalmanager.service;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.ukhanov.realhelpdesk.feature.portalmanager.exception.PortalException;

@Service
public class PortalUtilsService {

    private static final Logger logger = LoggerFactory.getLogger(PortalUtilsService.class);

    public boolean isValidUserId(String idStr) {
        if (idStr == null || idStr.isBlank()) {
            return false;
        }
        try {
            return Long.parseLong(idStr) > 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public void validateIdList(Set<Long> userIds) throws PortalException {
        for (Long userId : userIds) {
            if (userId == null || userId <= 0) {
                logger.debug("Неверный id пользователя: {}", userId);
                throw new PortalException("Неверный id пользователя: " + userId, HttpStatus.BAD_REQUEST);
            }
        }
    }
}
