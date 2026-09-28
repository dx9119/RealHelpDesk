package com.ukhanov.realhelpdesk.core.security.captcha.controller;

import com.ukhanov.realhelpdesk.core.security.ratelimit.annotation.RateLimit;
import com.ukhanov.realhelpdesk.core.security.captcha.service.CaptchaService;
import jakarta.validation.constraints.Size;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;


@RestController
@RequestMapping("/api/v1/captcha")
public class CaptchaController {

    private final CaptchaService captchaService;

    public CaptchaController(CaptchaService captchaService) {
        this.captchaService = captchaService;
    }

    @GetMapping
    @RateLimit(requests = 30, windowSeconds = 60)
    public ResponseEntity<byte[]> getCaptcha(
            @RequestParam
            @Size(max = 10) String capId
    ) throws IOException {

        byte[] imageBytes = captchaService.getImageBytes(capId);

        return ResponseEntity
                .ok()
                .contentType(MediaType.IMAGE_JPEG)
                .body(imageBytes);
    }

}
