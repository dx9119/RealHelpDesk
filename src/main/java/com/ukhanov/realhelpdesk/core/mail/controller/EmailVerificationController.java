package com.ukhanov.realhelpdesk.core.mail.controller;

import com.ukhanov.realhelpdesk.core.mail.exception.EmailConfirmationException;
import com.ukhanov.realhelpdesk.core.mail.service.MailService;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/email")
public class EmailVerificationController {

  private final MailService mailService;

  public EmailVerificationController(MailService mailService) {
    this.mailService = mailService;
  }

  @GetMapping("/confirm")
  public ResponseEntity<String> confirmEmail(@RequestParam("token") UUID token)
      throws EmailConfirmationException {
    mailService.confirmEmail(token);
    return ResponseEntity.ok("Email verified successfully!");
  }

}
