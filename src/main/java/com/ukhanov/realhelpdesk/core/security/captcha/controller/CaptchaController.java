package com.ukhanov.realhelpdesk.core.security.captcha.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ukhanov.realhelpdesk.core.http.ClientIpResolver;
import com.ukhanov.realhelpdesk.core.security.captcha.dto.ChallengeRequest;
import com.ukhanov.realhelpdesk.core.security.captcha.dto.ChallengeResponse;
import com.ukhanov.realhelpdesk.core.security.captcha.dto.SolveRequest;
import com.ukhanov.realhelpdesk.core.security.captcha.dto.SolveResponse;
import com.ukhanov.realhelpdesk.core.security.captcha.exception.CaptchaException;
import com.ukhanov.realhelpdesk.core.security.captcha.service.CaptchaService;
import com.ukhanov.realhelpdesk.core.security.ratelimit.annotation.RateLimit;

import lombok.RequiredArgsConstructor;

/**
 * Публичные эндпоинты капчи: выдать challenge (PoW или эмодзи-задание) и принять решение в обмен на подписанный captcha-токен. Оба белые в
 * {@code WhiteUrlConfig} — вызываются анонимным клиентом до регистрации. IP клиента уходит в сервис — по нему считается риск эскалации.
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/captcha")
public class CaptchaController {

    private final CaptchaService captchaService;
    private final ClientIpResolver clientIpResolver;

    @PostMapping("/challenge")
    @RateLimit(key = "captcha-challenge")
    public ResponseEntity<ChallengeResponse> challenge(@Valid @RequestBody ChallengeRequest request, HttpServletRequest httpRequest)
            throws CaptchaException {
        return ResponseEntity.ok(captchaService.challenge(request.action(), clientIpResolver.resolve(httpRequest)));
    }

    @PostMapping("/solve")
    @RateLimit(key = "captcha-solve")
    public ResponseEntity<SolveResponse> solve(@Valid @RequestBody SolveRequest request, HttpServletRequest httpRequest)
            throws CaptchaException {
        return ResponseEntity
                .ok(captchaService.solve(request.challengeId(), request.nonce(), request.answer(), clientIpResolver.resolve(httpRequest)));
    }
}
