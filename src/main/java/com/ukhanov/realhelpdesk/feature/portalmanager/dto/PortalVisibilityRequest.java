package com.ukhanov.realhelpdesk.feature.portalmanager.dto;

import jakarta.validation.constraints.NotNull;

public class PortalVisibilityRequest {

    @NotNull(message = "Признак публичности портала обязателен")
    private Boolean isPublic;

    public PortalVisibilityRequest() {
    }

    public PortalVisibilityRequest(Boolean isPublic) {
        this.isPublic = isPublic;
    }

    public Boolean getIsPublic() {
        return isPublic;
    }

    public void setIsPublic(Boolean isPublic) {
        this.isPublic = isPublic;
    }
}
