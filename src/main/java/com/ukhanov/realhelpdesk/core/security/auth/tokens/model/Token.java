package com.ukhanov.realhelpdesk.core.security.auth.tokens.model;

public class TokenAccess implements TokenBearer{

  private String token;

  public TokenAccess(String token) {
    this.token = token;
  }

  public void setToken(String token) {
    this.token = token;
  }

  @Override
  public String getToken() {
    return "";
  }
}
