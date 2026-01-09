package com.ukhanov.realhelpdesk.core.mail.controller;

import com.ukhanov.realhelpdesk.core.mail.dto.EmailInfoResponse;
import com.ukhanov.realhelpdesk.core.mail.exception.EmailAccessDeniedException;
import com.ukhanov.realhelpdesk.core.mail.model.NotificationEvent;
import com.ukhanov.realhelpdesk.core.mail.service.EmailDeliveryService;
import com.ukhanov.realhelpdesk.core.mail.service.EmailPolicyService;
import com.ukhanov.realhelpdesk.core.security.captcha.exception.CaptchaException;
import com.ukhanov.realhelpdesk.core.security.captcha.service.CaptchaService;
import jakarta.mail.MessagingException;

import java.io.UnsupportedEncodingException;
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
  private final CaptchaService captchaService;

  public EmailPolicyController(EmailDeliveryService emailDeliveryService, EmailPolicyService emailPolicyService, CaptchaService captchaService) {
    this.emailDeliveryService = emailDeliveryService;
    this.emailPolicyService = emailPolicyService;
    this.captchaService = captchaService;
  }

  @GetMapping("/confirm")
  public ResponseEntity<String> confirmEmail(
          @RequestParam("token") UUID token
  ) throws EmailAccessDeniedException {
    emailDeliveryService.confirmEmail(token);
    return ResponseEntity.ok("Успешно");
  }

  @GetMapping("/info")
  public ResponseEntity<EmailInfoResponse> getInfo(){
    EmailInfoResponse response = emailPolicyService.getEmailInfo();
    return ResponseEntity.ok(response);
  }

  @PostMapping("/notify-set")
  public ResponseEntity<String> stopNotify(@RequestParam NotificationEvent level) {
    emailPolicyService.addToStopList(level);
    return ResponseEntity.ok("Успешно");
  }

  @GetMapping("/code")
  public ResponseEntity<String> getCode(
          @RequestParam("capId") String capId,
          @RequestParam("capCode") String capCode
  ) throws MessagingException, CaptchaException, UnsupportedEncodingException {
    captchaService.captVerificationResult(capId, capCode);
    emailDeliveryService.sendConfirmCode();
    return ResponseEntity.ok("Успешно");
  }

}
