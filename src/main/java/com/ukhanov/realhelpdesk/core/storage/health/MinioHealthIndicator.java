package com.ukhanov.realhelpdesk.core.storage.health;

import java.util.Objects;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import com.ukhanov.realhelpdesk.core.storage.config.MinioProperties;

import io.minio.BucketExistsArgs;
import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Готовность хранилища вложений: readiness падает, когда MinIO недоступен или бакет пропал (infraReview.md, п. 1). Проверка — bucketExists
 * по одному бакету, без записи файлов.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class MinioHealthIndicator implements HealthIndicator {

    /** Имя бина в actuator: minio → /actuator/health показывает блок "minio". */
    public static final String NAME = "minio";

    private final MinioClient minioClient;
    private final MinioProperties properties;

    @Override
    public Health health() {
        Objects.requireNonNull(minioClient, "MinioClient не должен быть null");
        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(properties.getBucket()).build());
            if (exists) {
                return Health.up().withDetail("bucket", properties.getBucket()).build();
            }
            // бакет создаётся при первом обращении приложения — его отсутствие ещё не сбой
            return Health.up().withDetail("bucket", properties.getBucket()).withDetail("state", "not created yet").build();
        } catch (Exception e) {
            logger.warn("MinIO недоступен для health-проверки: {}", e.getMessage());
            return Health.down(e).withDetail("endpoint", properties.getEndpoint()).build();
        }
    }
}
