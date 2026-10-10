package com.ukhanov.realhelpdesk.feature.portalmanager.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PortalSettingsResponse(List<UserInfo> users, @JsonProperty("public") boolean isPublic) {
}
