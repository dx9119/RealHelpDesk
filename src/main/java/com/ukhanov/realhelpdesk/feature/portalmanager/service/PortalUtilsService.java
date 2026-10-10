package com.ukhanov.realhelpdesk.feature.portalmanager.service;

import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.ukhanov.realhelpdesk.feature.portalmanager.exception.PortalException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class PortalUtilsService {

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
