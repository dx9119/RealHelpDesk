package com.ukhanov.realhelpdesk.core.mail.dto;

public class TicketMessageNotificationDto {

  private final String subject;
  private final String message;

  private TicketMessageNotificationDto(Builder builder) {
    this.subject = builder.subject;
    this.message = builder.message;
  }

  public String getSubject() {
    return subject;
  }

  public String getMessage() {
    return message;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private String subject = "Новое сообщение в заявке";
    private String message;

    public Builder subject(String subject) {
      this.subject = subject;
      return this;
    }

    public Builder info(Long portalId, Long ticketId) {
      String link = "http://localhost:3001/portal/" + portalId + "/ticket/" + ticketId;
      this.message = """
                    Здравствуйте!

                    В заявке #%s появилось новое сообщение.

                    Ознакомиться с ним можно по ссылке:

                    %s

                    """.formatted(ticketId, link);
      return this;
    }

    public Builder message(String message) {
      this.message = message;
      return this;
    }

    public TicketMessageNotificationDto build() {
      return new TicketMessageNotificationDto(this);
    }
  }
}

