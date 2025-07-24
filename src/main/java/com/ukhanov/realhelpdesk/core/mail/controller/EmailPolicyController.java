package com.ukhanov.realhelpdesk.core.mail.controller;

import com.ukhanov.realhelpdesk.core.mail.dto.EmailInfoResponse;
import com.ukhanov.realhelpdesk.core.mail.exception.EmailAccessDeniedException;
import com.ukhanov.realhelpdesk.core.mail.model.NotificationEvent;
import com.ukhanov.realhelpdesk.core.mail.service.EmailDeliveryService;
import com.ukhanov.realhelpdesk.core.mail.service.EmailPolicyService;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/email")
public class EmailPolicyController {

  private final EmailDeliveryService emailDeliveryService;
  private final EmailPolicyService emailPolicyService;

  public EmailPolicyController(EmailDeliveryService emailDeliveryService, EmailPolicyService emailPolicyService) {
    this.emailDeliveryService = emailDeliveryService;
    this.emailPolicyService = emailPolicyService;
  }

  @GetMapping("/confirm")
  public ResponseEntity<String> confirmEmail(@RequestParam("token") UUID token)
      throws EmailAccessDeniedException {
    emailDeliveryService.confirmEmail(token);
    return ResponseEntity.ok("success");
  }

  @GetMapping("/info")
  public ResponseEntity<EmailInfoResponse> getInfo(){
    EmailInfoResponse response = emailPolicyService.getEmailInfo();
    return ResponseEntity.ok(response);
  }

  @PostMapping("/notify-set")
  public ResponseEntity<String> stopNotify(@RequestParam NotificationEvent level) {
    emailPolicyService.addToStopList(level);
    return ResponseEntity.ok("success");
  }

}
