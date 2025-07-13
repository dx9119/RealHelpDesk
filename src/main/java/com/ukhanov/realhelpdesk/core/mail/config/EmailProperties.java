package com.ukhanov.realhelpdesk.core.mail.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "email")
public class EmailProperties {
  private String notify;

  public String getNotify() {
    return notify;
  }

  public void setNotify(String notify) {
    this.notify = notify;
  }
}

