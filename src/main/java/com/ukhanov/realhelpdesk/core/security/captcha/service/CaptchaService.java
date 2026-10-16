package com.ukhanov.realhelpdesk.core.security.captcha.service;

import java.time.Instant;

import org.springframework.stereotype.Service;

import com.ukhanov.realhelpdesk.core.observability.AuthMetrics;
import com.ukhanov.realhelpdesk.core.security.captcha.config.CaptchaProperties;
import com.ukhanov.realhelpdesk.core.security.captcha.dto.ChallengeResponse;
import com.ukhanov.realhelpdesk.core.security.captcha.dto.SolveResponse;
import com.ukhanov.realhelpdesk.core.security.captcha.exception.CaptchaException;
import com.ukhanov.realhelpdesk.core.security.captcha.exception.CaptchaReason;
import com.ukhanov.realhelpdesk.core.security.captcha.observability.CaptchaMetrics;
import com.ukhanov.realhelpdesk.core.security.captcha.service.CaptchaTokenService.CaptchaTokenClaims;
import com.ukhanov.realhelpdesk.core.security.captcha.service.CaptchaTokenService.ChallengeClaims;
import com.ukhanov.realhelpdesk.core.security.captcha.utils.PowHash;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Оркестрация PoW-капчи: выдача challenge, приём решения с проверкой Proof-of-Work и проверка captcha-токена на бизнес-операциях.
 *
 * <p>
 * Проверка токена (fail-open при выключенной капче) и honeypot живут здесь, чтобы контроллеры знали про капчу только как про
 * {@code verify(...)}. Причины отказа капчурируются в {@link CaptchaException#getReason()} и уходят в метрики.
 * </p>
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class CaptchaService {

    private final CaptchaTokenService tokenService;
    private final UsedTokenStore usedTokenStore;
    private final CaptchaProperties properties;
    private final AuthMetrics authMetrics;
    private final CaptchaMetrics captchaMetrics;

    /**
     * Новое задание: генерирует id и соль, подписывает challenge с текущей сложностью. Сам подписанный JWT возвращается в поле
     * {@code challengeId} — solve принимает его без серверного состояния, а соль/сложность дублируются в params для клиента.
     */
    public ChallengeResponse challenge(String action) throws CaptchaException {
        if (!CaptchaTokenService.knownAction(action)) {
            throw new CaptchaException("Неизвестное действие капчи", CaptchaReason.INVALID);
        }
        String challengeId = tokenService.randomId();
        String salt = tokenService.randomId();
        String signedChallenge = tokenService.issueChallenge(action, challengeId, salt);
        Instant issuedAt = Instant.now();
        captchaMetrics.challengeIssued();
        logger.debug("Выдан challenge, action={}, challengeId={}", action, challengeId);
        return new ChallengeResponse(signedChallenge, ChallengeResponse.TYPE_POW,
                new ChallengeResponse.Params(salt, properties.getPowDifficulty()),
                issuedAt.plusSeconds(properties.getChallengeTtlSeconds()));
    }

    /**
     * Приём решения: подпись и срок challenge, неизрасходованность id, сам PoW. Успех — подписанный captcha-токен на действие challenge.
     */
    public SolveResponse solve(String challengeJwt, String nonce) throws CaptchaException {
        ChallengeClaims claims;
        try {
            claims = tokenService.parseChallenge(challengeJwt);
        } catch (CaptchaException e) {
            recordFailure(e.getReason());
            throw e;
        }

        if (nonce == null || nonce.isBlank()) {
            recordFailure(CaptchaReason.POW_FAILED);
            throw new CaptchaException("Провал прохождения капчи", CaptchaReason.POW_FAILED);
        }
        if (!PowHash.meets(claims.salt(), nonce, claims.difficulty())) {
            recordFailure(CaptchaReason.POW_FAILED);
            throw new CaptchaException("Провал прохождения капчи", CaptchaReason.POW_FAILED);
        }
        if (!usedTokenStore.firstUse("c:" + claims.challengeId())) {
            recordFailure(CaptchaReason.REPLAYED);
            throw new CaptchaException("Провал прохождения капчи", CaptchaReason.REPLAYED);
        }

        String captchaToken = tokenService.issueCaptchaToken(claims.action(), claims.issuedAt());
        logger.debug("Решение капчи принято, challengeId={}, action={}", claims.challengeId(), claims.action());
        return new SolveResponse(captchaToken);
    }

    /**
     * Проверка captcha-токена на бизнес-операции: подпись, срок, тип, действие, минимальное время заполнения и одноразовость. При
     * выключенной капче пропускается (fail-open, чтобы разработка без PoW не вставала).
     */
    public void verify(String captchaToken, String action) throws CaptchaException {
        if (!Boolean.TRUE.equals(properties.getEnabled())) {
            return;
        }
        doVerify(captchaToken, action);
    }

    /**
     * Проверка на регистрации: сначала honeypot (всегда, независимо от {@code captcha.enabled} — заполненное невидимое поле однозначно
     * бот), затем обычный verify токена.
     */
    public void verifyRegistration(String captchaToken, String honeypotValue) throws CaptchaException {
        if (honeypotValue != null && !honeypotValue.isBlank()) {
            recordFailure(CaptchaReason.HONEYPOT);
            throw new CaptchaException("Провал прохождения капчи", CaptchaReason.HONEYPOT);
        }
        verify(captchaToken, CaptchaTokenService.ACTION_REGISTER);
    }

    private void doVerify(String captchaToken, String action) throws CaptchaException {
        CaptchaTokenClaims claims;
        try {
            claims = tokenService.parseCaptchaToken(captchaToken, action);
        } catch (CaptchaException e) {
            recordFailure(e.getReason());
            throw e;
        }

        int minFill = properties.getMinFillSeconds();
        if (minFill > 0 && Instant.now().isBefore(claims.challengeIssuedAt().plusSeconds(minFill))) {
            recordFailure(CaptchaReason.TOO_FAST);
            throw new CaptchaException("Провал прохождения капчи", CaptchaReason.TOO_FAST);
        }
        if (!usedTokenStore.firstUse("t:" + claims.jti())) {
            recordFailure(CaptchaReason.REPLAYED);
            throw new CaptchaException("Провал прохождения капчи", CaptchaReason.REPLAYED);
        }

        recordSuccess();
        logger.debug("Captcha-токен принят, action={}", action);
    }

    private void recordFailure(CaptchaReason reason) {
        authMetrics.record(AuthMetrics.TYPE_CAPTCHA, AuthMetrics.RESULT_FAILURE);
        captchaMetrics.verification(false, reason);
    }

    private void recordSuccess() {
        authMetrics.record(AuthMetrics.TYPE_CAPTCHA, AuthMetrics.RESULT_SUCCESS);
        captchaMetrics.verification(true, CaptchaReason.OK);
    }
}
