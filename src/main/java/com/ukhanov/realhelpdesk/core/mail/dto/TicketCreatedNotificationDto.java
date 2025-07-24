package com.ukhanov.realhelpdesk.core.mail.dto;
public class TicketCreatedNotificationDto {

  private final String subject;
  private final String message;

  private TicketCreatedNotificationDto(Builder builder) {
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
    private String subject = "Создана новая заявка";
    private String message;

    public Builder subject(String subject) {
      this.subject = subject;
      return this;
    }

    public Builder info(Long portalId, Long ticketId) {
      String link = "http://localhost:3001/portal/" + portalId + "/applications";
      this.message = """
                    Здравствуйте!

                    В портале #%s была создана новая заявка #%s.

                    Вы можете просмотреть её по следующей ссылке:

                    %s

                    """.formatted(portalId,ticketId,link);
      return this;
    }

    public Builder message(String message) {
      this.message = message;
      return this;
    }

    public TicketCreatedNotificationDto build() {
      return new TicketCreatedNotificationDto(this);
    }
  }
}
