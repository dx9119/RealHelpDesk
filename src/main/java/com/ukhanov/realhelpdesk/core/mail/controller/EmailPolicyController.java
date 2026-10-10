package com.ukhanov.realhelpdesk.core.mail.controller;

import java.io.UnsupportedEncodingException;

import jakarta.mail.MessagingException;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ukhanov.realhelpdesk.core.mail.dto.EmailInfoResponse;
import com.ukhanov.realhelpdesk.core.mail.exception.EmailAccessDeniedException;
import com.ukhanov.realhelpdesk.core.mail.model.NotificationEvent;
import com.ukhanov.realhelpdesk.core.mail.service.EmailDeliveryService;
import com.ukhanov.realhelpdesk.core.mail.service.EmailPolicyService;
import com.ukhanov.realhelpdesk.core.security.captcha.exception.CaptchaException;
import com.ukhanov.realhelpdesk.core.security.captcha.service.CaptchaService;
import com.ukhanov.realhelpdesk.core.security.ratelimit.annotation.RateLimit;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/email")
public class EmailPolicyController {

    private final EmailDeliveryService emailDeliveryService;
    private final EmailPolicyService emailPolicyService;
    private final CaptchaService captchaService;

    @PostMapping("/confirmations/{token}")
    public ResponseEntity<Void> confirmEmail(@PathVariable Long token) throws EmailAccessDeniedException {
        emailDeliveryService.confirmEmail(token);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/info")
    public ResponseEntity<EmailInfoResponse> getInfo() {
        EmailInfoResponse response = emailPolicyService.getEmailInfo();
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/notifications/{event}")
    public ResponseEntity<Void> stopNotifications(@PathVariable NotificationEvent event) {
        emailPolicyService.addToStopList(event);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/codes")
    @RateLimit(key = "email-code")
    public ResponseEntity<Void> sendConfirmCode(@RequestParam(value = "capId", required = false) String capId,
            @RequestParam(value = "capCode", required = false) String capCode)
            throws MessagingException, CaptchaException, UnsupportedEncodingException {
        captchaService.captVerificationResult(capId, capCode);
        emailDeliveryService.sendConfirmCode();
        return ResponseEntity.accepted().build();
    }

}
