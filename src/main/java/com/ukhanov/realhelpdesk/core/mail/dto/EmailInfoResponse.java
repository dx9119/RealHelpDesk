package com.ukhanov.realhelpdesk.core.mail.dto;

import com.ukhanov.realhelpdesk.core.mail.model.NotificationEvent;

public class EmailInfoResponse {

  public EmailInfoResponse() {
  }

  private NotificationEvent muteLevel;

  public EmailInfoResponse(NotificationEvent muteLevel) {
    this.muteLevel = muteLevel;
  }

  public NotificationEvent getMuteLevel() {
    return muteLevel;
  }

  public void setMuteLevel(NotificationEvent muteLevel) {
    this.muteLevel = muteLevel;
  }
}
