package com.ukhanov.realhelpdesk.integration.telegram.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "telegram")
public class TelegramProperties {

  private String token;
  private String username;
  private String domain;
  private String webapp;

  public void setWebapp(String webapp) {
    this.webapp = webapp;
  }

  public String getWebapp() {
    return webapp;
  }

  public String getToken() {
    return token;
  }

  public String getDomain() {
    return domain;
  }

  public String getUsername() {
    return username;
  }

  public void setToken(String token) {
    this.token = token;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public void setDomain(String domain) {
    this.domain = domain;
  }
}
