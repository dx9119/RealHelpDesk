package com.ukhanov.realhelpdesk.core.storage.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Готовит бакет к работе при старте приложения.
 *
 * <p>
 * Ошибка здесь не валит приложение: хранилище может быть временно недоступно, а приложение без файлов всё равно работает (заявки,
 * сообщения). Незавершённая подготовка повторится при первой загрузке — см. {@link MinioStorageService#ensureBucket()}.
 * </p>
 */
@Component
public class StorageBucketInitializer implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(StorageBucketInitializer.class);

    private final MinioStorageService storageService;

    public StorageBucketInitializer(MinioStorageService storageService) {
        this.storageService = storageService;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            storageService.ensureBucket();
            logger.info("Хранилище файлов готово к работе");
        } catch (Exception e) {
            logger.error("Не удалось подготовить бакет хранилища на старте: {}. Приложение продолжит работу, "
                    + "подготовка повторится при первой загрузке файла.", e.getMessage());
        }
    }
}
