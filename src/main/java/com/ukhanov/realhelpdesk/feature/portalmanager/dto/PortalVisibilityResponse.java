package com.ukhanov.realhelpdesk.feature.portalmanager.dto;

public class PortalVisibilityResponse {

    private Boolean isPublic;

    public PortalVisibilityResponse() {
    }

    public PortalVisibilityResponse(Boolean isPublic) {
        this.isPublic = isPublic;
    }

    public Boolean getIsPublic() {
        return isPublic;
    }

    public void setIsPublic(Boolean isPublic) {
        this.isPublic = isPublic;
    }
}
