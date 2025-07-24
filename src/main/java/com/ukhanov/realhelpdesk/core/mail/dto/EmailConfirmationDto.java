  package com.ukhanov.realhelpdesk.core.mail.dto;

  import java.util.UUID;

  public class EmailConfirmationDto {

    private final String subject;
    private final String message;

    private EmailConfirmationDto(Builder builder) {
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
      private String subject = "Подтвердите адрес почты";
      private String message;

      public Builder subject(String subject) {
        this.subject = subject;
        return this;
      }

      public Builder token(UUID token) {
        String link = "http://localhost:8080/api/email/confirm?token=" + token;
        this.message = """
                      Здравствуйте!
  
                      Пожалуйста, подтвердите ваш адрес электронной почты по ссылке ниже:
  
                      %s
  
                      """.formatted(link);
        return this;
      }

      public Builder message(String message) {
        this.message = message;
        return this;
      }

      public EmailConfirmationDto build() {
        return new EmailConfirmationDto(this);
      }
    }
  }
