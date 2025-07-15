package com.ukhanov.realhelpdesk.integration.telegram.controller;

import com.ukhanov.realhelpdesk.integration.telegram.service.TelegramBotApplicationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.telegram.telegrambots.meta.api.objects.Update;

@RestController
@RequestMapping("/api/v1/telegram")
public class TelegramWebhookController {

  private static final Logger logger = LoggerFactory.getLogger(TelegramWebhookController.class);

  private final TelegramBotApplicationService botService;

  public TelegramWebhookController(TelegramBotApplicationService botService) {
    this.botService = botService;
  }

  @PostMapping("/notify-bot")
  public ResponseEntity<Void> onUpdateReceived(@RequestBody Update update) {
    logger.info("Get data from telegram: {}", update);
    botService.processUpdate(update);
    return ResponseEntity.ok().build();
  }


}