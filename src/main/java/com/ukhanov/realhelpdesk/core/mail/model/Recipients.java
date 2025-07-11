package com.ukhanov.realhelpdesk.core.mail.model;

public enum Recipients {
  ADMIN("event@realhelpdesk.com");

  private final String email;

  Recipients(String email) {
    this.email = email;
  }

  public String getEmail() {
    return email;
  }
}
