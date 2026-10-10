package com.ukhanov.realhelpdesk.core.security.auth.tokens.dto;

import java.time.Instant;

import com.ukhanov.realhelpdesk.core.security.auth.tokens.model.TokenStatus;

public record TokenStatusResponse(TokenStatus tokenStatus, Instant createdAt) {
}
