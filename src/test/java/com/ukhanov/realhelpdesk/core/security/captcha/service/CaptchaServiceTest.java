package com.ukhanov.realhelpdesk.core.security.captcha.service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.ukhanov.realhelpdesk.core.observability.AuthMetrics;
import com.ukhanov.realhelpdesk.core.security.captcha.config.CaptchaProperties;
import com.ukhanov.realhelpdesk.core.security.captcha.dto.ChallengeResponse;
import com.ukhanov.realhelpdesk.core.security.captcha.dto.SolveResponse;
import com.ukhanov.realhelpdesk.core.security.captcha.exception.CaptchaException;
import com.ukhanov.realhelpdesk.core.security.captcha.exception.CaptchaReason;
import com.ukhanov.realhelpdesk.core.security.captcha.observability.CaptchaMetrics;
import com.ukhanov.realhelpdesk.core.security.captcha.utils.PowHash;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import io.jsonwebtoken.Jwts;

@DisplayName("Оркестрация капчи: challenge, solve (PoW) и verify токена")
class CaptchaServiceTest {

    private static final String ACTION = CaptchaTokenService.ACTION_REGISTER;
    private static final int DIFFICULTY = 4;

    private SecretKey key;
    private CaptchaTokenService tokenService;
    private CaptchaProperties properties;
    private CaptchaService service;
    private AuthMetrics authMetrics;
    private CaptchaMetrics captchaMetrics;

    @BeforeEach
    void setUp() {
        key = new SecretKeySpec("test-only-captcha-hmac-secret-0123456789abcdef".getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        properties = new CaptchaProperties();
        properties.setEnabled(true);
        properties.setPowDifficulty(DIFFICULTY);
        properties.setChallengeTtlSeconds(300);
        properties.setTokenTtlSeconds(300);
        properties.setMinFillSeconds(0);
        tokenService = new CaptchaTokenService(key, properties);
        authMetrics = Mockito.mock(AuthMetrics.class);
        captchaMetrics = Mockito.mock(CaptchaMetrics.class);
        service = new CaptchaService(tokenService, new UsedTokenStore(300), properties, authMetrics, captchaMetrics);
    }

    // ────────────────────────────────────────────────
    // Challenge
    // ────────────────────────────────────────────────

    @Test
    @DisplayName("challenge: тип pow, challengeId — подписанный JWT, соль/сложность совпадают с подписью")
    void challenge_returnsPowAssignment() throws Exception {
        Instant before = Instant.now();

        ChallengeResponse response = service.challenge(ACTION);

        assertThat(response.type()).isEqualTo(ChallengeResponse.TYPE_POW);
        assertThat(response.params().salt()).hasSize(32).matches("[0-9a-f]{32}");
        assertThat(response.params().difficulty()).isEqualTo(DIFFICULTY);
        assertThat(response.expiresAt()).isBetween(before.plusSeconds(300), Instant.now().plusSeconds(305));

        CaptchaTokenService.ChallengeClaims claims = tokenService.parseChallenge(response.challengeId());
        assertThat(claims.challengeId()).hasSize(32).matches("[0-9a-f]{32}");
        assertThat(claims.salt()).isEqualTo(response.params().salt());
        assertThat(claims.difficulty()).isEqualTo(DIFFICULTY);
        assertThat(claims.action()).isEqualTo(ACTION);
        verify(captchaMetrics).challengeIssued();
    }

    @Test
    @DisplayName("challenge с неизвестным действием — INVALID, метрика не растёт")
    void challenge_unknownAction_invalid() {
        assertThatThrownBy(() -> service.challenge("other")).isInstanceOf(CaptchaException.class)
                .extracting(e -> ((CaptchaException) e).getReason()).isEqualTo(CaptchaReason.INVALID);

        verifyNoInteractions(captchaMetrics);
    }

    // ────────────────────────────────────────────────
    // Solve
    // ────────────────────────────────────────────────

    @Test
    @DisplayName("solve с верным nonce возвращает подписанный captcha-токен на то же действие")
    void solve_correctNonce_returnsCaptchaToken() throws Exception {
        ChallengeResponse challenge = service.challenge(ACTION);
        String nonce = solve(challenge);

        SolveResponse response = service.solve(challenge.challengeId(), nonce);

        CaptchaTokenService.CaptchaTokenClaims claims = tokenService.parseCaptchaToken(response.captchaToken(), ACTION);
        assertThat(claims.jti()).hasSize(32);
        assertThat(claims.challengeIssuedAt()).isNotNull();
    }

    @Test
    @DisplayName("solve с неверным nonce — POW_FAILED и фиксируется как провал")
    void solve_wrongNonce_powFailed() throws CaptchaException {
        ChallengeResponse challenge = service.challenge(ACTION);

        assertThatThrownBy(() -> service.solve(challenge.challengeId(), "not-a-solution")).isInstanceOf(CaptchaException.class)
                .extracting(e -> ((CaptchaException) e).getReason()).isEqualTo(CaptchaReason.POW_FAILED);

        verify(authMetrics).record(AuthMetrics.TYPE_CAPTCHA, AuthMetrics.RESULT_FAILURE);
        verify(captchaMetrics).verification(false, CaptchaReason.POW_FAILED);
    }

    @Test
    @DisplayName("Повторное решение того же challenge — REPLAYED")
    void solve_replay_rejected() throws Exception {
        ChallengeResponse challenge = service.challenge(ACTION);
        String nonce = solve(challenge);
        service.solve(challenge.challengeId(), nonce);

        assertThatThrownBy(() -> service.solve(challenge.challengeId(), nonce)).isInstanceOf(CaptchaException.class)
                .extracting(e -> ((CaptchaException) e).getReason()).isEqualTo(CaptchaReason.REPLAYED);
    }

    @Test
    @DisplayName("Подделанный challenge — INVALID, решение не принимается")
    void solve_tamperedChallenge_invalid() throws CaptchaException {
        ChallengeResponse challenge = service.challenge(ACTION);
        String jwt = challenge.challengeId();
        String tampered = jwt.substring(0, jwt.length() - 2) + "aa";

        assertThatThrownBy(() -> service.solve(tampered, "0")).isInstanceOf(CaptchaException.class)
                .extracting(e -> ((CaptchaException) e).getReason()).isEqualTo(CaptchaReason.INVALID);
    }

    @Test
    @DisplayName("Истёкший challenge — EXPIRED")
    void solve_expiredChallenge_expired() {
        Instant past = Instant.now().minusSeconds(600);
        String expired = Jwts.builder().claim("typ", CaptchaTokenService.TYPE_CHALLENGE).claim("act", ACTION).claim("cid", "cid")
                .claim("salt", "salt").claim("dif", DIFFICULTY).issuedAt(Date.from(past)).expiration(Date.from(past.plusSeconds(300)))
                .signWith(key).compact();

        assertThatThrownBy(() -> service.solve(expired, "0")).isInstanceOf(CaptchaException.class)
                .extracting(e -> ((CaptchaException) e).getReason()).isEqualTo(CaptchaReason.EXPIRED);
    }

    // ────────────────────────────────────────────────
    // Verify
    // ────────────────────────────────────────────────

    @Test
    @DisplayName("verify принимает свежий токен и пишет успех в метрики")
    void verify_validToken_success() throws Exception {
        String token = tokenService.issueCaptchaToken(ACTION, Instant.now());

        service.verify(token, ACTION);

        verify(authMetrics).record(AuthMetrics.TYPE_CAPTCHA, AuthMetrics.RESULT_SUCCESS);
        verify(captchaMetrics).verification(true, CaptchaReason.OK);
    }

    @Test
    @DisplayName("verify без токена — MISSING_TOKEN с фиксацией промаха")
    void verify_missingToken_reason() {
        assertThatThrownBy(() -> service.verify(null, ACTION)).isInstanceOf(CaptchaException.class)
                .extracting(e -> ((CaptchaException) e).getReason()).isEqualTo(CaptchaReason.MISSING_TOKEN);

        verify(authMetrics).record(AuthMetrics.TYPE_CAPTCHA, AuthMetrics.RESULT_FAILURE);
        verify(captchaMetrics).verification(false, CaptchaReason.MISSING_TOKEN);
    }

    @Test
    @DisplayName("Токен чужого действия — WRONG_ACTION")
    void verify_wrongAction_reason() {
        String token = tokenService.issueCaptchaToken(CaptchaTokenService.ACTION_EMAIL_CODE, Instant.now());

        assertThatThrownBy(() -> service.verify(token, ACTION)).isInstanceOf(CaptchaException.class)
                .extracting(e -> ((CaptchaException) e).getReason()).isEqualTo(CaptchaReason.WRONG_ACTION);
    }

    @Test
    @DisplayName("Повторный предъявленный токен — REPLAYED")
    void verify_replay_rejected() throws Exception {
        String token = tokenService.issueCaptchaToken(ACTION, Instant.now());
        service.verify(token, ACTION);

        assertThatThrownBy(() -> service.verify(token, ACTION)).isInstanceOf(CaptchaException.class)
                .extracting(e -> ((CaptchaException) e).getReason()).isEqualTo(CaptchaReason.REPLAYED);
    }

    @Test
    @DisplayName("Отправка раньше minimum-fill — TOO_FAST")
    void verify_tooFast_reason() {
        properties.setMinFillSeconds(5);
        String token = tokenService.issueCaptchaToken(ACTION, Instant.now().minusSeconds(1));

        assertThatThrownBy(() -> service.verify(token, ACTION)).isInstanceOf(CaptchaException.class)
                .extracting(e -> ((CaptchaException) e).getReason()).isEqualTo(CaptchaReason.TOO_FAST);
    }

    @Test
    @DisplayName("Капча выключена — fail-open, даже без токена регистрация проходит")
    void verify_disabled_passes() throws CaptchaException {
        properties.setEnabled(false);

        service.verify(null, ACTION);
    }

    // ────────────────────────────────────────────────
    // Honeypot (регистрация)
    // ────────────────────────────────────────────────

    @Test
    @DisplayName("Заполненный honeypot — HONEYPOT, даже при выключенной капче; пробелы считаются пустым полем")
    void verifyRegistration_honeypotFilled_rejected() throws CaptchaException {
        properties.setEnabled(false);

        service.verifyRegistration(null, "   ");
        assertThatThrownBy(() -> service.verifyRegistration(null, "https://spam.example")).isInstanceOf(CaptchaException.class)
                .extracting(e -> ((CaptchaException) e).getReason()).isEqualTo(CaptchaReason.HONEYPOT);
        verify(authMetrics).record(AuthMetrics.TYPE_CAPTCHA, AuthMetrics.RESULT_FAILURE);
        verify(captchaMetrics).verification(false, CaptchaReason.HONEYPOT);
    }

    @Test
    @DisplayName("Пустой honeypot — проверка уходит в обычный verify токена")
    void verifyRegistration_emptyHoneypot_delegatesToVerify() throws Exception {
        String token = tokenService.issueCaptchaToken(ACTION, Instant.now());

        service.verifyRegistration(token, "");

        verify(captchaMetrics).verification(true, CaptchaReason.OK);
    }

    private String solve(ChallengeResponse challenge) {
        for (long i = 0; i < 100_000; i++) {
            String nonce = Long.toString(i);
            if (PowHash.meets(challenge.params().salt(), nonce, challenge.params().difficulty())) {
                return nonce;
            }
        }
        throw new IllegalStateException("nonce не найден");
    }
}
