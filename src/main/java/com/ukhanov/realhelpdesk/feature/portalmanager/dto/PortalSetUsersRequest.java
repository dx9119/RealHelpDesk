package com.ukhanov.realhelpdesk.feature.portalmanager.dto;

import java.util.Set;

import jakarta.validation.constraints.Size;

public class PortalSetUsersRequest {
    @Size(max = 100, message = "Нельзя назначить более 100 пользователей за один запрос")
    private Set<Long> newAccessUserId;

    public Set<Long> getNewAccessUserId() {
        return newAccessUserId;
    }

    public void setNewAccessUserId(Set<Long> newAccessUserId) {
        this.newAccessUserId = newAccessUserId;
    }
}
