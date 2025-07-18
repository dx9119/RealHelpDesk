package com.ukhanov.realhelpdesk.feature.portalmanager.dto;

import java.util.List;
import java.util.UUID;

public class PortalAccessRequest {

  private List<UUID> newAccessUserId;
  private boolean isPublic;

  public List<UUID> getNewAccessUserId() {
    return newAccessUserId;
  }

  public void setNewAccessUserId(List<UUID> newAccessUserId) {
    this.newAccessUserId = newAccessUserId;
  }

  public boolean isPublic() {
    return isPublic;
  }

  public void setPublic(boolean aPublic) {
    isPublic = aPublic;
  }
}

