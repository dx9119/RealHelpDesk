package com.ukhanov.realhelpdesk.feature.portalmanager.dto;

import java.time.Instant;

/**
 * Запись истории портала для участников: передачи владения и изменения портала. Имена участников — best effort (удалённый пользователь).
 */
public record PortalHistoryResponse(Long id, Long portalId, String event, Long actorId, String actorName, Long targetUserId,
        String targetName, String reason, String fieldName, String oldValue, String newValue, Instant createdAt) {
}
