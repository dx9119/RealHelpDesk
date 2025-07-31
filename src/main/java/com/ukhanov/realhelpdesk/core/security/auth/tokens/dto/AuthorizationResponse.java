package com.ukhanov.realhelpdesk.core.security.auth.tokens.dto;

public class AuthorizationResponse {
  private String authorization;

  public AuthorizationResponse() {}

  public AuthorizationResponse(String authorization) {
    this.authorization = authorization;
  }

  public String getAuthorization() {
    return authorization;
  }

  public void setAuthorization(String authorization) {
    this.authorization = authorization;
  }
}

