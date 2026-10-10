package com.ukhanov.realhelpdesk.core.security.captcha.controller;

import java.io.IOException;

import jakarta.validation.constraints.Size;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ukhanov.realhelpdesk.core.security.captcha.service.CaptchaService;
import com.ukhanov.realhelpdesk.core.security.ratelimit.annotation.RateLimit;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/captcha")
public class CaptchaController {

    private final CaptchaService captchaService;

    @GetMapping
    @RateLimit(key = "captcha")
    public ResponseEntity<byte[]> getCaptcha(@RequestParam @Size(max = 10) String capId) throws IOException {

        byte[] imageBytes = captchaService.getImageBytes(capId);

        return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).body(imageBytes);
    }

}
