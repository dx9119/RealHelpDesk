package com.ukhanov.realhelpdesk.core.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
public class HealthCheckController {

  @GetMapping("/api/v1/health-check")
  public Map<String, Object> checkHealth() {
    Map<String, Object> response = new HashMap<>();
    response.put("Статус", "Активен");
    response.put("Время", LocalDateTime.now().toString());
    return response;
  }
}

