package com.ukhanov.realhelpdesk.integration.telegram.controller;

import com.ukhanov.realhelpdesk.integration.telegram.service.TelegramSecurityService;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/telegram")
public class TelegramInitDataController {

  private final TelegramSecurityService telegramSecurityService;

  public TelegramInitDataController(TelegramSecurityService telegramSecurityService) {
    this.telegramSecurityService = telegramSecurityService;
  }

  @PostMapping(value = "/webapp-data", consumes = "text/plain")
  public ResponseEntity<String> setAccess(@RequestBody String initDataRaw)
      throws NoSuchAlgorithmException, InvalidKeyException {

    if(!telegramSecurityService.verifyInitData(initDataRaw)) {
      return ResponseEntity.badRequest().build();
    }

    return ResponseEntity.ok().build();
  }


}
