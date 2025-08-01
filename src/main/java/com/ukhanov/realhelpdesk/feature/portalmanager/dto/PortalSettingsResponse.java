package com.ukhanov.realhelpdesk.feature.portalmanager.dto;

import java.util.List;

public class PortalSettingsResponse {
  private List<UserInfo> users;
  private boolean isPublic;

  public PortalSettingsResponse(List<UserInfo> users, boolean isPublic) {
    this.users = users;
    this.isPublic = isPublic;
  }

  public List<UserInfo> getUsers() {
    return users;
  }

  public void setUsers(List<UserInfo> users) {
    this.users = users;
  }

  public boolean isPublic() {
    return isPublic;
  }

  public void setPublic(boolean aPublic) {
    isPublic = aPublic;
  }
}
