package com.ukhanov.realhelpdesk.core.security.captcha.service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Date;
import java.util.HexFormat;
import java.util.Set;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Service;

import com.ukhanov.realhelpdesk.core.security.captcha.config.CaptchaProperties;
import com.ukhanov.realhelpdesk.core.security.captcha.exception.CaptchaException;
import com.ukhanov.realhelpdesk.core.security.captcha.exception.CaptchaReason;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;

/**
 * Подпись и проверка токенов капчи (stateless: ответы и соль не хранятся, состояние — только подписи и {@code iat/exp}). Два типа токенов
 * под одним секретом:
 *
 * <ul>
 * <li><b>challenge</b> — задание, выданное клиенту: {@code typ=challenge}, действие ({@code act}), id ({@code cid}), соль ({@code salt}) и
 * сложность ({@code dif});</li>
 * <li><b>captcha-токен</b> — доказательство решения, предъявляемое на бизнес-операции: {@code typ=captcha}, то же {@code act}, свой
 * одноразовый id ({@code jti}) и время выдачи challenge ({@code ciat}) для проверки минимального времени заполнения.</li>
 * </ul>
 *
 * <p>
 * Тип в подписи не даёт подменить challenge токеном (и наоборот), привязка {@code act} — не пропустить токен чужого действия; просрочка и
 * подделка подписи ловятся разбором JWT.
 * </p>
 */
@Service
public class CaptchaTokenService {

    /** Токен-задание (salt, difficulty), выдаётся на POST /api/v1/captcha/challenge. */
    public static final String TYPE_CHALLENGE = "challenge";

    /** Решённое доказательство, предъявляется на register / email-code. */
    public static final String TYPE_CAPTCHA = "captcha";

    /** Действия, на которые выпускаются токены: токен register не принимается на email-code и наоборот. */
    public static final String ACTION_REGISTER = "register";
    public static final String ACTION_EMAIL_CODE = "email-code";

    private static final Set<String> ACTIONS = Set.of(ACTION_REGISTER, ACTION_EMAIL_CODE);

    private static final String CLAIM_TYPE = "typ";
    private static final String CLAIM_ACTION = "act";
    private static final String CLAIM_CHALLENGE_ID = "cid";
    private static final String CLAIM_SALT = "salt";
    private static final String CLAIM_DIFFICULTY = "dif";
    private static final String CLAIM_JTI = "jti";
    private static final String CLAIM_CHALLENGE_ISSUED_AT = "ciat";

    private static final int ID_BYTES = 16;

    private final SecretKey captchaSecretKey;
    private final CaptchaProperties properties;
    private final SecureRandom random = new SecureRandom();

    public CaptchaTokenService(SecretKey captchaSecretKey, CaptchaProperties properties) {
        this.captchaSecretKey = captchaSecretKey;
        this.properties = properties;
        Integer difficulty = properties.getPowDifficulty();
        Integer challengeTtl = properties.getChallengeTtlSeconds();
        Integer tokenTtl = properties.getTokenTtlSeconds();
        Integer minFill = properties.getMinFillSeconds();
        if (difficulty == null || difficulty < 1 || difficulty > 26) {
            throw new IllegalStateException("captcha.pow-difficulty должен быть в диапазоне 1..26, получено " + difficulty);
        }
        if (challengeTtl == null || challengeTtl < 10 || challengeTtl > 86_400) {
            throw new IllegalStateException("captcha.challenge-ttl-seconds должен быть в диапазоне 10..86400, получено " + challengeTtl);
        }
        if (tokenTtl == null || tokenTtl < 10 || tokenTtl > 86_400) {
            throw new IllegalStateException("captcha.token-ttl-seconds должен быть в диапазоне 10..86400, получено " + tokenTtl);
        }
        if (minFill == null || minFill < 0 || minFill > 3_600) {
            throw new IllegalStateException("captcha.min-fill-seconds должен быть в диапазоне 0..3600, получено " + minFill);
        }
    }

    /** Случайный 128-битный идентификатор в hex: id challenge или id токена. */
    public String randomId() {
        byte[] bytes = new byte[ID_BYTES];
        random.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    /** Подписанный challenge на действие: сложность берётся из конфигурации (клиент её не выбирает). */
    public String issueChallenge(String action, String challengeId, String salt) {
        requireAction(action);
        Instant now = Instant.now();
        return Jwts.builder().claim(CLAIM_TYPE, TYPE_CHALLENGE).claim(CLAIM_ACTION, action).claim(CLAIM_CHALLENGE_ID, challengeId)
                .claim(CLAIM_SALT, salt).claim(CLAIM_DIFFICULTY, properties.getPowDifficulty()).issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(properties.getChallengeTtlSeconds()))).signWith(captchaSecretKey).compact();
    }

    /** Разбор challenge: подпись, срок и тип; клиентское действие проверяется при выдаче токена. */
    public ChallengeClaims parseChallenge(String token) throws CaptchaException {
        Claims claims = parse(token);
        if (!TYPE_CHALLENGE.equals(claims.get(CLAIM_TYPE, String.class))) {
            throw new CaptchaException("Провал прохождения капчи", CaptchaReason.WRONG_TYPE);
        }
        String action = claims.get(CLAIM_ACTION, String.class);
        String challengeId = claims.get(CLAIM_CHALLENGE_ID, String.class);
        String salt = claims.get(CLAIM_SALT, String.class);
        Integer difficulty = claims.get(CLAIM_DIFFICULTY, Integer.class);
        if (challengeId == null || salt == null || difficulty == null || !ACTIONS.contains(action)) {
            throw new CaptchaException("Провал прохождения капчи", CaptchaReason.INVALID);
        }
        return new ChallengeClaims(challengeId, salt, difficulty, action,
                claims.getIssuedAt() != null ? claims.getIssuedAt().toInstant() : Instant.EPOCH);
    }

    /** Подписанный captcha-токен на действие, с временем выдачи challenge для проверки minimum-fill. */
    public String issueCaptchaToken(String action, Instant challengeIssuedAt) {
        requireAction(action);
        Instant now = Instant.now();
        return Jwts.builder().claim(CLAIM_TYPE, TYPE_CAPTCHA).claim(CLAIM_ACTION, action).id(randomId())
                .claim(CLAIM_CHALLENGE_ISSUED_AT, Date.from(challengeIssuedAt)).issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(properties.getTokenTtlSeconds()))).signWith(captchaSecretKey).compact();
    }

    /** Разбор captcha-токена: подпись, срок, тип и привязка к действию. */
    public CaptchaTokenClaims parseCaptchaToken(String token, String expectedAction) throws CaptchaException {
        requireAction(expectedAction);
        Claims claims = parse(token);
        if (!TYPE_CAPTCHA.equals(claims.get(CLAIM_TYPE, String.class))) {
            throw new CaptchaException("Провал прохождения капчи", CaptchaReason.WRONG_TYPE);
        }
        if (!expectedAction.equals(claims.get(CLAIM_ACTION, String.class))) {
            throw new CaptchaException("Провал прохождения капчи", CaptchaReason.WRONG_ACTION);
        }
        String jti = claims.getId();
        Date challengeIssuedAt = claims.get(CLAIM_CHALLENGE_ISSUED_AT, Date.class);
        if (jti == null || jti.isBlank() || challengeIssuedAt == null) {
            throw new CaptchaException("Провал прохождения капчи", CaptchaReason.INVALID);
        }
        return new CaptchaTokenClaims(jti, challengeIssuedAt.toInstant());
    }

    private Claims parse(String token) throws CaptchaException {
        if (token == null || token.isBlank()) {
            throw new CaptchaException("Провал прохождения капчи", CaptchaReason.MISSING_TOKEN);
        }
        try {
            return Jwts.parser().verifyWith(captchaSecretKey).build().parseSignedClaims(token).getPayload();
        } catch (ExpiredJwtException e) {
            throw new CaptchaException("Провал прохождения капчи", CaptchaReason.EXPIRED, e);
        } catch (JwtException | IllegalArgumentException e) {
            throw new CaptchaException("Провал прохождения капчи", CaptchaReason.INVALID, e);
        }
    }

    private void requireAction(String action) {
        if (!ACTIONS.contains(action)) {
            throw new IllegalArgumentException("Неизвестное действие капчи: " + action);
        }
    }

    /** Разобранный challenge: id, соль и сложность для проверки PoW, действие — для выданного токена. */
    public record ChallengeClaims(String challengeId, String salt, int difficulty, String action, Instant issuedAt) {
    }

    /** Разобранный captcha-токен: одноразовый id и время выдачи challenge. */
    public record CaptchaTokenClaims(String jti, Instant challengeIssuedAt) {
    }
}
