package com.ukhanov.realhelpdesk;


import com.ukhanov.realhelpdesk.core.mail.service.MailService;
import jakarta.mail.MessagingException;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;


@SpringBootApplication
public class RealHelpDeskApplication {

  private final MailService mailService;

  public RealHelpDeskApplication(MailService mailService) {
    this.mailService = mailService;
  }

  public static void main(String[] args) {
        SpringApplication.run(RealHelpDeskApplication.class, args);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void verifyMailSystem() throws MessagingException {
        mailService.sendAdminNotification("Mail check", "Email system is working");
    }

}