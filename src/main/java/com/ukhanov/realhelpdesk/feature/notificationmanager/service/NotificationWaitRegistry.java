package com.ukhanov.realhelpdesk.feature.notificationmanager.service;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Реестр ожидающих long-polling запросов оповещений: один инстанс приложения, поэтому достаточно общих структур в памяти. Клиент
 * регистрирует колбэк ({@code onWake}), публикатор будит его после коммита новой записи — ожидающий запрос сразу перечитывает БД и
 * отвечает, не дожидаясь таймаута.
 */
@Component
public class NotificationWaitRegistry {

    private static final Logger logger = LoggerFactory.getLogger(NotificationWaitRegistry.class);

    private final ConcurrentMap<Long, List<Runnable>> waiters = new ConcurrentHashMap<>();

    public void register(Long userId, Runnable onWake) {
        if (userId == null || onWake == null) {
            return;
        }
        waiters.computeIfAbsent(userId, id -> new CopyOnWriteArrayList<>()).add(onWake);
    }

    public void unregister(Long userId, Runnable onWake) {
        if (userId == null || onWake == null) {
            return;
        }
        List<Runnable> userWaiters = waiters.get(userId);
        if (userWaiters == null) {
            return;
        }
        userWaiters.remove(onWake);
        if (userWaiters.isEmpty()) {
            waiters.remove(userId, userWaiters);
        }
    }

    /** Будит всех ожидающих пользователя; реестр очищается — каждый колбэк срабатывает один раз. */
    public void wake(Long userId) {
        if (userId == null) {
            return;
        }
        List<Runnable> userWaiters = waiters.remove(userId);
        if (userWaiters == null) {
            return;
        }
        for (Runnable onWake : userWaiters) {
            try {
                onWake.run();
            } catch (Exception e) {
                logger.warn("Ошибка пробуждения ожидающего запроса оповещений пользователя {}", userId, e);
            }
        }
    }

    public int waiterCount(Long userId) {
        List<Runnable> userWaiters = waiters.get(userId);
        return userWaiters == null ? 0 : userWaiters.size();
    }
}
