package com.ukhanov.realhelpdesk.core.security.localjwt.auth.tokens.dto;

import java.time.Instant;

import com.ukhanov.realhelpdesk.core.security.localjwt.auth.tokens.model.TokenStatus;

public record TokenStatusResponse(TokenStatus tokenStatus, Instant createdAt) {
}
