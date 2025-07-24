package com.ukhanov.realhelpdesk.feature.portalmanager.dto;

import java.util.Set;
import java.util.UUID;

public class PortalSettingsResponse {
  private Set<UUID> users;
  private boolean isPublic;

  public Set<UUID> getUsers() {
    return users;
  }

  public void setUsers(Set<UUID> users) {
    this.users = users;
  }

  public boolean isPublic() {
    return isPublic;
  }

  public void setPublic(boolean aPublic) {
    isPublic = aPublic;
  }
}
