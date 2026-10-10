package com.ukhanov.realhelpdesk.feature.messagemanager.dto;

import java.time.Instant;

public record MessageResponse(Long id, String messageText, String authorFullName, Long ticketId, Instant createdAt) {
}
