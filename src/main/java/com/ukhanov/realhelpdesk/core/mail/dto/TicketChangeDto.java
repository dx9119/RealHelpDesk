package com.ukhanov.realhelpdesk.core.mail.dto;

public class TicketChangeDto {

  private String subject;

  private String message;

  public TicketChangeDto(String subject, String message) {
    this.subject = subject;
    this.message = message;
  }

  public String getSubject() {
    return subject;
  }

  public void setSubject(String subject) {
    this.subject = subject;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }



}
