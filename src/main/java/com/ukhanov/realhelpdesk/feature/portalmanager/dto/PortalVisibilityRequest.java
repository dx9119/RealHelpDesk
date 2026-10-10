package com.ukhanov.realhelpdesk.feature.portalmanager.dto;

import jakarta.validation.constraints.NotNull;

public record PortalVisibilityRequest(@NotNull(message = "Признак публичности портала обязателен") Boolean isPublic) {
}
