package com.ukhanov.realhelpdesk.core.security.captcha.service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ukhanov.realhelpdesk.core.security.captcha.config.CaptchaProperties;
import com.ukhanov.realhelpdesk.core.security.captcha.exception.CaptchaException;
import com.ukhanov.realhelpdesk.core.security.captcha.exception.CaptchaReason;
import com.ukhanov.realhelpdesk.core.security.captcha.service.CaptchaTokenService.CaptchaTokenClaims;
import com.ukhanov.realhelpdesk.core.security.captcha.service.CaptchaTokenService.ChallengeClaims;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.jsonwebtoken.Jwts;

@DisplayName("Токены капчи: подпись, разбор и отказы по типу/действию/сроку")
class CaptchaTokenServiceTest {

    private CaptchaTokenService service;
    private SecretKey key;

    @BeforeEach
    void setUp() {
        key = new SecretKeySpec("test-only-captcha-hmac-secret-0123456789abcdef".getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        CaptchaProperties properties = properties();
        service = new CaptchaTokenService(key, properties);
    }

    @Test
    @DisplayName("challenge: выпускается и разбирается с теми же id, солью, сложностью и действием")
    void challenge_roundTrip() throws CaptchaException {
        Instant before = Instant.now().minusSeconds(5);
        String token = service.issueChallenge(CaptchaTokenService.ACTION_REGISTER, "cid123", "salt456");
        ChallengeClaims claims = service.parseChallenge(token);

        assertThat(claims.challengeId()).isEqualTo("cid123");
        assertThat(claims.salt()).isEqualTo("salt456");
        assertThat(claims.difficulty()).isEqualTo(18);
        assertThat(claims.action()).isEqualTo("register");
        assertThat(claims.level()).isEqualTo(CaptchaTokenService.LEVEL_POW);
        assertThat(claims.issuedAt()).isBetween(before, Instant.now().plusSeconds(5));
    }

    @Test
    @DisplayName("challenge с явной сложностью: значение из аргумента попадает в подпись (эскалация PoW)")
    void challenge_explicitDifficulty_roundTrip() throws CaptchaException {
        String token = service.issueChallenge(CaptchaTokenService.ACTION_REGISTER, "cid123", "salt456", 21);

        assertThat(service.parseChallenge(token).difficulty()).isEqualTo(21);
    }

    @Test
    @DisplayName("challenge с недопустимой сложностью (0 или выше максимума) — IllegalArgumentException")
    void challenge_invalidDifficulty_throws() {
        assertThatThrownBy(() -> service.issueChallenge(CaptchaTokenService.ACTION_REGISTER, "cid", "salt", 0))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Сложность PoW");
        assertThatThrownBy(() -> service.issueChallenge(CaptchaTokenService.ACTION_REGISTER, "cid", "salt",
                CaptchaTokenService.MAX_POW_DIFFICULTY + 1)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Сложность PoW");
    }

    @Test
    @DisplayName("emoji-challenge: выпускается и разбирается с теми же id, заданием, глифами и HMAC ответа")
    void emojiChallenge_roundTrip() throws CaptchaException {
        Instant before = Instant.now().minusSeconds(5);
        List<String> cells = List.of("🚗", "🐱", "🍎", "🍕", "💡", "🌳", "🐶", "📱", "🌻");
        String answerHash = service.answerHash("cid-emoji", List.of(0, 2));
        String token = service.issueEmojiChallenge(CaptchaTokenService.ACTION_REGISTER, "cid-emoji", "Выберите фрукты", cells, answerHash);

        ChallengeClaims claims = service.parseChallenge(token);

        assertThat(claims.challengeId()).isEqualTo("cid-emoji");
        assertThat(claims.level()).isEqualTo(CaptchaTokenService.LEVEL_EMOJI);
        assertThat(claims.prompt()).isEqualTo("Выберите фрукты");
        assertThat(claims.cells()).isEqualTo(cells);
        assertThat(claims.answerHash()).isEqualTo(answerHash);
        assertThat(claims.salt()).isNull();
        assertThat(claims.difficulty()).isNull();
        assertThat(claims.issuedAt()).isBetween(before, Instant.now().plusSeconds(5));
    }

    @Test
    @DisplayName("answerHash: одинаковый набор индексов в разном порядке даёт один HMAC, другой набор — другой")
    void answerHash_orderInsensitiveDifferentAnswerDiffers() {
        String first = service.answerHash("cid", List.of(0, 2, 5));
        String shuffled = service.answerHash("cid", List.of(5, 0, 2));
        String other = service.answerHash("cid", List.of(0, 2, 6));
        String otherCid = service.answerHash("cid2", List.of(0, 2, 5));

        assertThat(first).isEqualTo(shuffled).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(other).isNotEqualTo(first);
        assertThat(otherCid).isNotEqualTo(first);
    }

    @Test
    @DisplayName("emoji-challenge без задания или глифов — IllegalArgumentException")
    void emojiChallenge_invalidArgs_throws() {
        assertThatThrownBy(() -> service.issueEmojiChallenge(CaptchaTokenService.ACTION_REGISTER, "cid", "  ", List.of("🚗", "🐱"), "hash"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.issueEmojiChallenge(CaptchaTokenService.ACTION_REGISTER, "cid", "prompt", List.of("🚗"), "hash"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("captcha-токен: несёт своё действие, jti, источник решения и время выдачи challenge")
    void captchaToken_roundTrip() throws CaptchaException {
        // JWT сериализует даты с точностью до секунд — сравниваем с такой же точностью
        Instant challengeIssuedAt = Instant.now().minusSeconds(30).truncatedTo(ChronoUnit.SECONDS);
        String token = service.issueCaptchaToken(CaptchaTokenService.ACTION_EMAIL_CODE, challengeIssuedAt, CaptchaTokenService.LEVEL_EMOJI);
        CaptchaTokenClaims claims = service.parseCaptchaToken(token, CaptchaTokenService.ACTION_EMAIL_CODE);

        assertThat(claims.jti()).hasSize(32);
        assertThat(claims.challengeIssuedAt()).isEqualTo(challengeIssuedAt);
        assertThat(claims.source()).isEqualTo(CaptchaTokenService.LEVEL_EMOJI);
    }

    @Test
    @DisplayName("captcha-токен с неизвестным источником — IllegalArgumentException")
    void captchaToken_unknownSource_throws() {
        assertThatThrownBy(() -> service.issueCaptchaToken(CaptchaTokenService.ACTION_REGISTER, Instant.now(), "provider"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("provider");
    }

    @Test
    @DisplayName("Подделанный токен (битая подпись) — INVALID")
    void tamperedToken_invalid() {
        String token = service.issueCaptchaToken(CaptchaTokenService.ACTION_REGISTER, Instant.now(), CaptchaTokenService.LEVEL_POW);
        String tampered = token.substring(0, token.length() - 2) + "aa";

        assertReason(() -> service.parseCaptchaToken(tampered, CaptchaTokenService.ACTION_REGISTER), CaptchaReason.INVALID);
    }

    @Test
    @DisplayName("Пустой или отсутствующий токен — MISSING_TOKEN")
    void missingToken_reason() {
        assertReason(() -> service.parseCaptchaToken(null, CaptchaTokenService.ACTION_REGISTER), CaptchaReason.MISSING_TOKEN);
        assertReason(() -> service.parseCaptchaToken("   ", CaptchaTokenService.ACTION_REGISTER), CaptchaReason.MISSING_TOKEN);
        assertReason(() -> service.parseChallenge(null), CaptchaReason.MISSING_TOKEN);
    }

    @Test
    @DisplayName("Токен-задание вместо captcha-токена (и наоборот) — WRONG_TYPE")
    void wrongType_reason() {
        String challenge = service.issueChallenge(CaptchaTokenService.ACTION_REGISTER, "cid", "salt");
        String captcha = service.issueCaptchaToken(CaptchaTokenService.ACTION_REGISTER, Instant.now(), CaptchaTokenService.LEVEL_POW);

        assertReason(() -> service.parseCaptchaToken(challenge, CaptchaTokenService.ACTION_REGISTER), CaptchaReason.WRONG_TYPE);
        assertReason(() -> service.parseChallenge(captcha), CaptchaReason.WRONG_TYPE);
    }

    @Test
    @DisplayName("Токен одного действия предъявлен на другое — WRONG_ACTION")
    void wrongAction_reason() {
        String token = service.issueCaptchaToken(CaptchaTokenService.ACTION_REGISTER, Instant.now(), CaptchaTokenService.LEVEL_POW);

        assertReason(() -> service.parseCaptchaToken(token, CaptchaTokenService.ACTION_EMAIL_CODE), CaptchaReason.WRONG_ACTION);
    }

    @Test
    @DisplayName("Истёкший токен — EXPIRED (свой же секрет, срок в прошлом)")
    void expiredToken_reason() {
        Instant past = Instant.now().minusSeconds(60);
        String expired = Jwts.builder().claim("typ", CaptchaTokenService.TYPE_CAPTCHA).claim("act", CaptchaTokenService.ACTION_REGISTER)
                .id("jti00000000000000000000000000000").claim("src", CaptchaTokenService.LEVEL_POW).claim("ciat", Date.from(past))
                .issuedAt(Date.from(past)).expiration(Date.from(past.plusSeconds(30))).signWith(key).compact();

        assertReason(() -> service.parseCaptchaToken(expired, CaptchaTokenService.ACTION_REGISTER), CaptchaReason.EXPIRED);
    }

    @Test
    @DisplayName("Неизвестное действие в выпуске — IllegalArgumentException, а не тихий токен")
    void unknownAction_throws() {
        assertThatThrownBy(() -> service.issueChallenge("other", "cid", "salt")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("other");
    }

    @Test
    @DisplayName("Невалидные числовые свойства — старт падает с понятной ошибкой")
    void constructor_validatesProperties() {
        CaptchaProperties tooEasy = properties();
        tooEasy.setPowDifficulty(0);
        assertThatThrownBy(() -> new CaptchaTokenService(key, tooEasy)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("pow-difficulty");

        CaptchaProperties longTtl = properties();
        longTtl.setTokenTtlSeconds(1);
        assertThatThrownBy(() -> new CaptchaTokenService(key, longTtl)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("token-ttl-seconds");

        CaptchaProperties negativeMinFill = properties();
        negativeMinFill.setMinFillSeconds(-1);
        assertThatThrownBy(() -> new CaptchaTokenService(key, negativeMinFill)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("min-fill-seconds");

        CaptchaProperties missing = properties();
        missing.setChallengeTtlSeconds(null);
        assertThatThrownBy(() -> new CaptchaTokenService(key, missing)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("challenge-ttl-seconds");
    }

    private CaptchaProperties properties() {
        CaptchaProperties properties = new CaptchaProperties();
        properties.setEnabled(true);
        properties.setPowDifficulty(18);
        properties.setChallengeTtlSeconds(300);
        properties.setTokenTtlSeconds(300);
        properties.setMinFillSeconds(0);
        return properties;
    }

    private void assertReason(ThrowingRunnable action, CaptchaReason expected) {
        assertThatThrownBy(action::run).isInstanceOf(CaptchaException.class).extracting(e -> ((CaptchaException) e).getReason())
                .isEqualTo(expected);
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Throwable;
    }
}
