package com.ukhanov.realhelpdesk.core.storage.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Подключение к MinIO: адрес, учётные данные и бакет для файлов вложений заявок.
 *
 * <p>
 * Значения — только переменные окружения (см. application.properties): адрес сервиса и учётки приходят из docker-compose.yaml, имя бакета —
 * из docker/app.env. Файлы лежат в MinIO, в БД хранятся только метаданные (attachment_model).
 * </p>
 */
@Component
@ConfigurationProperties(prefix = "minio")
public class MinioProperties {

    /** Адрес API MinIO, например http://minio:9000 — имя сервиса из compose, не localhost. */
    private String endpoint;

    /** Доступ (MINIO_ROOT_USER / аналогичная учётная запись с правами на бакет). */
    private String accessKey;

    /** Секрет доступа, хранится в .env и подставляется через docker-compose.yaml. */
    private String secretKey;

    /** Бакет с файлами вложений; создаётся автоматически при первом обращении. */
    private String bucket;

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }
}
