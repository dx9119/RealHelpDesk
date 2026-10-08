package com.ukhanov.realhelpdesk.core.storage.config;

import java.util.Objects;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.minio.MinioClient;

/**
 * Клиент MinIO: один бин на всё приложение, закрывается контекстом (MinioClient implements AutoCloseable).
 */
@Configuration
public class MinioClientConfig {

    @Bean
    public MinioClient minioClient(MinioProperties properties) {
        Objects.requireNonNull(properties, "Настройки MinIO не должны быть null");
        Objects.requireNonNull(properties.getEndpoint(), "minio.endpoint не задан");
        Objects.requireNonNull(properties.getAccessKey(), "minio.access-key не задан");
        Objects.requireNonNull(properties.getSecretKey(), "minio.secret-key не задан");

        return MinioClient.builder().endpoint(properties.getEndpoint()).credentials(properties.getAccessKey(), properties.getSecretKey())
                .build();
    }
}
