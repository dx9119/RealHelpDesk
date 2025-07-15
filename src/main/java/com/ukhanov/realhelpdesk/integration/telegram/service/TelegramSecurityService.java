package com.ukhanov.realhelpdesk.integration.telegram.service;

import com.ukhanov.realhelpdesk.integration.telegram.config.TelegramProperties;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class TelegramSecurityService {

  private static final Logger logger = LoggerFactory.getLogger(TelegramSecurityService.class);
  private final TelegramProperties telegramProperties;

  public TelegramSecurityService(TelegramProperties telegramProperties) {
    this.telegramProperties = telegramProperties;
  }

  // Проверяет корректность подписи initData от Telegram WebApp
  public boolean verifyInitData(String initDataRaw)
      throws NoSuchAlgorithmException, InvalidKeyException {
    Map<String, String> dataMap = new LinkedHashMap<>();
    String[] lines = initDataRaw.split("\n");
    String receivedHash = null;

    // Разбираем пары ключ=значение и извлекаем hash
    for (String line : lines) {
      int idx = line.indexOf('=');
      if (idx > 0) {
        String key = line.substring(0, idx);
        String value = line.substring(idx + 1);
        if (key.equals("hash")) {
          receivedHash = value;
        } else {
          dataMap.put(key, value);
        }
      }
    }

    // Проверка: hash отсутствует
    if (receivedHash == null) {
      logger.info("received Hash:{}", receivedHash);
      return false;
    }

    // Сортируем остальные параметры и собираем строку для верификации
    String dataCheckString = dataMap.entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .map(e -> e.getKey() + "=" + e.getValue())
        .reduce((a, b) -> a + "\n" + b)
        .orElse("");

    // Вычисляем секретный ключ и подпись для проверки
    byte[] secretKey = hmacSha256("WebAppData", telegramProperties.getToken());
    String calculatedHash = hmacSha256Hex(secretKey, dataCheckString);

    // Сравниваем рассчитанную подпись с полученной
    if (calculatedHash.equals(receivedHash)) {
      return true;
    } else {
      logger.info(calculatedHash + " != " + receivedHash);
      return false;
    }
  }

  // Генерирует HMAC-SHA256 в байтах по строковому ключу
  private byte[] hmacSha256(String key, String message)
      throws NoSuchAlgorithmException, InvalidKeyException {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    return mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
  }

  // Генерирует HMAC-SHA256 в hex по байтовому ключу
  private String hmacSha256Hex(byte[] key, String message)
      throws NoSuchAlgorithmException, InvalidKeyException {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(key, "HmacSHA256"));
    byte[] hash = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
    StringBuilder hex = new StringBuilder();
    for (byte b : hash) {
      hex.append(String.format("%02x", b));
    }
    return hex.toString();
  }

}

