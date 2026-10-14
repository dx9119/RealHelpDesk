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

        MinioClient client = MinioClient.builder().endpoint(properties.getEndpoint())
                .credentials(properties.getAccessKey(), properties.getSecretKey()).build();
        // SDK по умолчанию ждёт до 5 минут на каждую сетевую фазу: health-проверка (obs.md, шаг 9) обязана
        // отвечать за секунды, а зависшая загрузка не должна держать поток минутами. connect 1.5 с — общий
        // предел для всех операций; чтение/запись 10 с — пауза дольше 10 секунд означает мёртвый канал.
        client.setTimeout(1500, 10000, 10000);
        return client;
    }
}
