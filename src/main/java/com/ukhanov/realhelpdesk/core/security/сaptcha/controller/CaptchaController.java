package com.ukhanov.realhelpdesk.core.security.сaptcha.controller;

import com.ukhanov.realhelpdesk.core.security.сaptcha.service.CaptchaService;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;
import java.time.Duration;


@RestController
@RequestMapping("/api/v1/captcha")
public class CaptchaController {

    private final CaptchaService captchaService;

    public CaptchaController(CaptchaService captchaService) {
        this.captchaService = captchaService;
    }

    @GetMapping
    public ResponseEntity<byte[]> getCaptcha(
            @RequestParam
            @Size(max = 10) String capId
    ) throws IOException {

        byte[] imageBytes = captchaService.getImageBytes(capId);
        String encodedCaptcha = captchaService.getEncodedCaptchaText(capId);

        ResponseCookie captchaCookie = ResponseCookie.from("captcha", encodedCaptcha)
                .httpOnly(true)
                .secure(true)
                .path("/")
                .maxAge(Duration.ofMinutes(30))
                .sameSite("None")
                .build();

        return ResponseEntity
                .ok()
                .header(HttpHeaders.SET_COOKIE, captchaCookie.toString())
                .contentType(MediaType.IMAGE_JPEG)
                .body(imageBytes);
    }

}

