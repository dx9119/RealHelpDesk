package com.ukhanov.realhelpdesk.feature.portalmanager.dto;

import java.util.Set;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class PortalUsersRequest {

    @NotNull(message = "Список пользователей обязателен")
    @Size(max = 100, message = "Нельзя назначить более 100 пользователей за один запрос")
    private Set<Long> userIds;

    public PortalUsersRequest() {
    }

    public PortalUsersRequest(Set<Long> userIds) {
        this.userIds = userIds;
    }

    public Set<Long> getUserIds() {
        return userIds;
    }

    public void setUserIds(Set<Long> userIds) {
        this.userIds = userIds;
    }
}
