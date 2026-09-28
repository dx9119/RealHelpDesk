package com.ukhanov.realhelpdesk.feature.portalmanager.dto;

import jakarta.validation.constraints.Size;

import java.util.Set;

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
