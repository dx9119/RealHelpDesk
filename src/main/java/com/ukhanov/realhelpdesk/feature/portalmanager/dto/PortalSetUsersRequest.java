package com.ukhanov.realhelpdesk.feature.portalmanager.dto;

import jakarta.validation.constraints.Size;

import java.util.Set;
import java.util.UUID;

public class PortalSetUsersRequest {
  @Size(max = 100, message = "Нельзя назначить более 100 пользователей за один запрос")
  private Set<UUID> newAccessUserId;

  public Set<UUID> getNewAccessUserId() {
    return newAccessUserId;
  }

  public void setNewAccessUserId(Set<UUID> newAccessUserId) {
    this.newAccessUserId = newAccessUserId;
  }
}
