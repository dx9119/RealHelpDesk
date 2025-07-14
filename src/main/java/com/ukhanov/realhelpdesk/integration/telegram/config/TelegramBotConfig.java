package com.ukhanov.realhelpdesk.integration.telegram.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "telegram.enabled", havingValue = "true", matchIfMissing = false)
@ComponentScan(basePackages = "com.ukhanov.realhelpdesk.integration.telegram")
public class TelegramBotConfig {
  private String token;
  private String username;
  private String domain;

  public String getToken() {
    return token;
  }

  public String getDomain() {
    return domain;
  }

  public void setDomain(String domain) {
    this.domain = domain;
  }

  public void setToken(String token) {
    this.token = token;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }
}
