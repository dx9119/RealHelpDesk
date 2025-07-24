package com.ukhanov.realhelpdesk.feature.portalmanager.dto;

import java.util.Set;
import java.util.UUID;

public class PortalSetUsersRequest {
  private Set<UUID> newAccessUserId;

  public Set<UUID> getNewAccessUserId() {
    return newAccessUserId;
  }

  public void setNewAccessUserId(Set<UUID> newAccessUserId) {
    this.newAccessUserId = newAccessUserId;
  }
}
