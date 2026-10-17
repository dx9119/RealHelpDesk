package com.ukhanov.realhelpdesk.core.security.auth.tokens.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.ukhanov.realhelpdesk.core.config.JwtProperties;
import com.ukhanov.realhelpdesk.core.filter.LoggingFilter;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.model.TokenStatus;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.repository.JwtRefreshTokenRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * Ротация refresh-токенов: удаляет накопившиеся строки {@code jwt_tokens}. Отзыв (логаут, смена пароля, ротация при входе) меняет только
 * статус, а протухшие активные строки остаются, — поэтому без очистки таблица растёт неограниченно.
 *
 * <p>
 * Два срока:
 * </p>
 * <ul>
 * <li>отозванные строки (статус не {@code ACTIVE}) старше {@code jwt.revoked-token-retention-days};</li>
 * <li>активные строки старше срока жизни refresh-токена ({@code jwt.refresh-token-expiration}, минуты) — токен уже недействителен по
 * {@code exp}.</li>
 * </ul>
 *
 * <p>
 * Удаление не монолитное: сначала оценивается объём ({@code COUNT} по индексу {@code created_at}), затем строки удаляются батчами — каждый
 * батч в своей короткой транзакции ({@link RefreshTokenCleanupExecutor}), между батчами пауза. Размер батча адаптивный: вырос в пределах
 * {@code jwt.token-cleanup-batch-min-size}..{@code jwt.token-cleanup-batch-max-size} при быстром батче и уменьшился при медленном
 * относительно {@code jwt.token-cleanup-target-batch-ms}. Так очистка даёт ровную нагрузку без пиков.
 * </p>
 *
 * <p>
 * Расписание — {@code jwt.token-cleanup-cron} (6 полей, с секундами). Значения проверяются на старте.
 * </p>
 */
@Slf4j
@Service
public class RefreshTokenCleanupService {

    /** Технический контекст фонового запуска: у планировщика нет запроса, к которому привязать MDC. */
    private static final String SCHEDULER_REQUEST_ID = "scheduler-token-cleanup";
    private static final String SCHEDULER_URI = "scheduled:token-cleanup";

    private final JwtRefreshTokenRepository jwtRefreshTokenRepository;
    private final RefreshTokenCleanupExecutor batchExecutor;
    private final JwtProperties jwtProperties;

    public RefreshTokenCleanupService(JwtRefreshTokenRepository jwtRefreshTokenRepository, RefreshTokenCleanupExecutor batchExecutor,
            JwtProperties jwtProperties) {
        this.jwtRefreshTokenRepository = jwtRefreshTokenRepository;
        this.batchExecutor = batchExecutor;
        this.jwtProperties = jwtProperties;
        Integer retentionDays = jwtProperties.getRevokedTokenRetentionDays();
        if (retentionDays == null || retentionDays < 1) {
            throw new IllegalStateException("jwt.revoked-token-retention-days должен быть положительным, получено " + retentionDays);
        }
        Integer refreshMinutes = jwtProperties.getRefreshTokenExpiration();
        if (refreshMinutes == null || refreshMinutes < 1) {
            throw new IllegalStateException("jwt.refresh-token-expiration должен быть положительным, получено " + refreshMinutes);
        }
        if (jwtProperties.getTokenCleanupCron() == null || jwtProperties.getTokenCleanupCron().isBlank()) {
            throw new IllegalStateException("jwt.token-cleanup-cron не содержит значение");
        }
        Integer batchMin = jwtProperties.getTokenCleanupBatchMinSize();
        Integer batchMax = jwtProperties.getTokenCleanupBatchMaxSize();
        Integer targetMs = jwtProperties.getTokenCleanupTargetBatchMs();
        Integer pauseMs = jwtProperties.getTokenCleanupBatchPauseMs();
        if (batchMin == null || batchMin < 1) {
            throw new IllegalStateException("jwt.token-cleanup-batch-min-size должен быть положительным, получено " + batchMin);
        }
        if (batchMax == null || batchMax < batchMin) {
            throw new IllegalStateException(
                    "jwt.token-cleanup-batch-max-size должен быть не меньше min-size, получено " + batchMax + " < " + batchMin);
        }
        if (targetMs == null || targetMs < 1) {
            throw new IllegalStateException("jwt.token-cleanup-target-batch-ms должен быть положительным, получено " + targetMs);
        }
        if (pauseMs == null || pauseMs < 0) {
            throw new IllegalStateException("jwt.token-cleanup-batch-pause-ms не может быть отрицательным, получено " + pauseMs);
        }
    }

    @Scheduled(cron = "${jwt.token-cleanup-cron}")
    public void deleteExpiredTokens() {
        MDC.put(LoggingFilter.REQUEST_ID, SCHEDULER_REQUEST_ID);
        MDC.put(LoggingFilter.URI_REQUEST, SCHEDULER_URI);
        try {
            Instant now = Instant.now();
            Instant inactiveCutoff = now.minus(jwtProperties.getRevokedTokenRetentionDays(), ChronoUnit.DAYS);
            Instant activeCutoff = now.minus(jwtProperties.getRefreshTokenExpiration(), ChronoUnit.MINUTES);

            long estimatedInactive = jwtRefreshTokenRepository.countByStatusNotAndCreatedAtBefore(TokenStatus.ACTIVE, inactiveCutoff);
            long estimatedActive = jwtRefreshTokenRepository.countByStatusAndCreatedAtBefore(TokenStatus.ACTIVE, activeCutoff);
            logger.info("Ротация refresh-токенов: оценка объёма — отозванных {}, протухших {}, стартовый батч {} ({}..{})",
                    estimatedInactive, estimatedActive, jwtProperties.getTokenCleanupBatchMinSize(),
                    jwtProperties.getTokenCleanupBatchMinSize(), jwtProperties.getTokenCleanupBatchMaxSize());

            BatchResult inactive = purge(true, inactiveCutoff);
            BatchResult active = purge(false, activeCutoff);

            int deleted = inactive.deleted() + active.deleted();
            if (deleted > 0) {
                // INFO, а не DEBUG: в prod должно быть видно, что ротация работает
                logger.info("Ротация refresh-токенов: удалено строк — отозванных {} ({} батчей), протухших {} ({} батчей)",
                        inactive.deleted(), inactive.batches(), active.deleted(), active.batches());
            } else {
                logger.debug("Ротация refresh-токенов: удалять нечего");
            }
        } finally {
            MDC.clear();
        }
    }

    private BatchResult purge(boolean inactive, Instant cutoff) {
        int minBatch = jwtProperties.getTokenCleanupBatchMinSize();
        int maxBatch = jwtProperties.getTokenCleanupBatchMaxSize();
        int batchSize = minBatch;
        int total = 0;
        int batches = 0;
        while (true) {
            long startNanos = System.nanoTime();
            int deleted = inactive
                    ? batchExecutor.deleteInactiveBatch(cutoff, batchSize)
                    : batchExecutor.deleteActiveBatch(cutoff, batchSize);
            long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;
            if (deleted == 0) {
                break;
            }
            total += deleted;
            batches++;
            int requested = batchSize;
            batchSize = adaptBatchSize(batchSize, elapsedMs, jwtProperties.getTokenCleanupTargetBatchMs(), minBatch, maxBatch);
            if (deleted < requested) {
                // Взяли меньше, чем просили: подходящих строк больше нет
                break;
            }
            if (!pause()) {
                logger.warn("Ротация refresh-токенов: пауза прервана, прогон остановлен на {} удалённых строках", total);
                break;
            }
        }
        return new BatchResult(total, batches);
    }

    /** Адаптивный размер батча: медленный батч уменьшается, быстрый (вдвое быстрее цели) растёт, границы — min..max. */
    static int adaptBatchSize(int current, long elapsedMs, int targetMs, int minBatch, int maxBatch) {
        if (elapsedMs > targetMs && current > minBatch) {
            return Math.max(minBatch, current / 2);
        }
        if (elapsedMs < targetMs / 2 && current < maxBatch) {
            return Math.min(maxBatch, current * 2);
        }
        return current;
    }

    /** Пауза между батчами; {@code false} — поток прерван, прогон нужно завершить. */
    private boolean pause() {
        int pauseMs = jwtProperties.getTokenCleanupBatchPauseMs();
        if (pauseMs <= 0) {
            return true;
        }
        try {
            Thread.sleep(pauseMs);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /** Итог удаления одной группы: сколько удалено и за сколько батчей. */
    private record BatchResult(int deleted, int batches) {
    }
}
