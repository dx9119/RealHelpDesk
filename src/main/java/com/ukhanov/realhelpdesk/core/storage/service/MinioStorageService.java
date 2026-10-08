package com.ukhanov.realhelpdesk.core.storage.service;

import java.io.InputStream;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.ukhanov.realhelpdesk.core.storage.config.MinioProperties;
import com.ukhanov.realhelpdesk.core.storage.exception.StorageException;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.errors.ErrorResponseException;

/**
 * Хранилище файлов поверх MinIO: бакет создаётся автоматически, объекты адресуются ключом вида {@code tickets/{ticketId}/{uuid}.{ext}}.
 *
 * <p>
 * Все ошибки SDK сворачиваются в {@link StorageException} (503, объект не найден — 404): клиент MinIO бросает пачку checked-исключений, и
 * раскладывать их по слоям выше смысла нет — это одна и та же «хранилище не ответило».
 * </p>
 */
@Service
public class MinioStorageService {

    private static final Logger logger = LoggerFactory.getLogger(MinioStorageService.class);

    private final MinioClient minioClient;
    private final MinioProperties properties;

    /** После успешной проверки бакет не перепроверяется на каждой операции; при сбое флаг не поднимается и проверка повторится. */
    private volatile boolean bucketReady;

    public MinioStorageService(MinioClient minioClient, MinioProperties properties) {
        this.minioClient = Objects.requireNonNull(minioClient, "minioClient не должен быть null");
        this.properties = Objects.requireNonNull(properties, "Настройки MinIO не должны быть null");
    }

    /**
     * Идемпотентная подготовка бакета: создаёт его при отсутствии. Вызывается на старте (StorageBucketInitializer) и перед каждой
     * операцией, пока не пройдёт успешно.
     */
    public void ensureBucket() {
        if (bucketReady) {
            return;
        }
        String bucket = properties.getBucket();
        try {
            if (!minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                logger.info("Создан бакет хранилища файлов: {}", bucket);
            }
            bucketReady = true;
        } catch (Exception e) {
            throw new StorageException("Хранилище файлов недоступно, бакет " + bucket + " не подготовлен", e);
        }
    }

    /** Потоковая запись: файл не держится в памяти приложения целиком. */
    public void upload(String objectKey, InputStream content, long size, String contentType) {
        Objects.requireNonNull(objectKey, "Ключ объекта не должен быть null");
        Objects.requireNonNull(content, "Содержимое файла не должно быть null");
        if (size <= 0) {
            throw new IllegalArgumentException("Размер файла должен быть положительным: " + objectKey);
        }
        ensureBucket();
        try {
            // partSize = -1: размер известен, а размер части MinIO посчитает сам (5MiB — его минимум, файл меньше лимита — одна часть).
            minioClient.putObject(PutObjectArgs.builder().bucket(properties.getBucket()).object(objectKey).stream(content, size, -1)
                    .contentType(contentType).build());
            logger.debug("Файл сохранён в хранилище: {}", objectKey);
        } catch (Exception e) {
            throw new StorageException("Не удалось сохранить файл в хранилище", e);
        }
    }

    /**
     * Открывает поток чтения целиком. Владение потоком — на вызывающем: закрыть его обязательно, иначе соединение с MinIO повиснет.
     * Обращение к бакету, которого ещё нет, — это не 404, а «хранилище не готово» (503), поэтому ensureBucket вызывается и здесь.
     */
    public InputStream download(String objectKey) {
        return download(objectKey, 0, -1);
    }

    /**
     * Открывает поток чтения фрагмента {@code [offset, offset + length)} — MinIO отдаёт только запрошенные байты (HTTP Range к объекту),
     * поэтому на нестабильной сети фрагмент не тянет за собой весь файл. {@code length < 0} — до конца объекта. Владение потоком — на
     * вызывающем.
     */
    public InputStream download(String objectKey, long offset, long length) {
        Objects.requireNonNull(objectKey, "Ключ объекта не должен быть null");
        if (offset < 0) {
            throw new IllegalArgumentException("Смещение фрагмента не может быть отрицательным: " + offset);
        }
        ensureBucket();
        try {
            GetObjectArgs.Builder builder = GetObjectArgs.builder().bucket(properties.getBucket()).object(objectKey);
            if (offset > 0) {
                builder.offset(offset);
            }
            if (length >= 0) {
                builder.length(length);
            }
            return minioClient.getObject(builder.build());
        } catch (ErrorResponseException e) {
            if (e.errorResponse() != null && "NoSuchKey".equals(e.errorResponse().code())) {
                throw StorageException.notFound("Файл не найден в хранилище");
            }
            throw new StorageException("Не удалось получить файл из хранилища", e);
        } catch (Exception e) {
            throw new StorageException("Не удалось получить файл из хранилища", e);
        }
    }

    /**
     * Best-effort удаление: нужно для отката неудачной загрузки, поэтому ошибки только логируются — падать из-за уже неудачной операции
     * нельзя.
     */
    public void delete(String objectKey) {
        if (objectKey == null) {
            return;
        }
        try {
            minioClient.removeObject(RemoveObjectArgs.builder().bucket(properties.getBucket()).object(objectKey).build());
            logger.debug("Файл удалён из хранилища: {}", objectKey);
        } catch (Exception e) {
            logger.warn("Не удалось удалить файл из хранилища {}: {}", objectKey, e.getMessage());
        }
    }
}
