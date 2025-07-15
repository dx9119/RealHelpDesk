package com.ukhanov.realhelpdesk.integration.telegram.service;

import com.ukhanov.realhelpdesk.integration.telegram.config.TelegramProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.bots.TelegramWebhookBot;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.webapp.WebAppInfo;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.Collections;

@Service
public class TelegramBotApplicationService extends TelegramWebhookBot {

  private static final Logger logger = LoggerFactory.getLogger(TelegramBotApplicationService.class);
  private final TelegramProperties config;

  public TelegramBotApplicationService(TelegramProperties config) {
    super(config.getToken());
    this.config = config;
  }


  public void processUpdate(Update update) {
    logger.info("Received update");
    if (update == null) {
      logger.info("Received null update");
      return;
    }

    // 1. Обработка команды /start
    if (update.hasMessage() && update.getMessage().hasText()) {
      String text = update.getMessage().getText();
      Long chatId = update.getMessage().getChatId();

      if ("/start".equals(text)) {
        sendWebAppButton(chatId);
      }
    }
  }

  private void sendWebAppButton(Long chatId) {
    WebAppInfo webAppInfo = new WebAppInfo(config.getWebapp());

    InlineKeyboardButton button = new InlineKeyboardButton();
    button.setText("Demo button");
    button.setWebApp(webAppInfo);

    InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
    markup.setKeyboard(Collections.singletonList(Collections.singletonList(button)));

    SendMessage message = new SendMessage();
    message.setChatId(chatId.toString());
    message.setText("Open WebApp");
    message.setReplyMarkup(markup);

    try {
      execute(message);
    } catch (TelegramApiException e) {
      logger.error("Failed to send WebApp button", e);
    }
  }

  @Override
  public BotApiMethod<?> onWebhookUpdateReceived(Update update) {
    return null;
  }

  @Override
  public String getBotPath() {
    return "";
  }

  @Override
  public String getBotUsername() {
    return config.getUsername();
  }
}