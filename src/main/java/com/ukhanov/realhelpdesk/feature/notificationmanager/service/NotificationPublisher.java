package com.ukhanov.realhelpdesk.feature.notificationmanager.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.ukhanov.realhelpdesk.core.mail.model.NotificationEvent;
import com.ukhanov.realhelpdesk.domain.notification.model.NotificationModel;
import com.ukhanov.realhelpdesk.domain.notification.model.UserNotificationPreferencesModel;
import com.ukhanov.realhelpdesk.domain.notification.repository.NotificationRepository;
import com.ukhanov.realhelpdesk.domain.notification.repository.UserNotificationPreferencesRepository;
import com.ukhanov.realhelpdesk.domain.portal.model.PortalModel;

/**
 * Фан-аут in-app оповещений: те же получатели, что у email-оповещений (владелец портала + доверенные), без автора действия и с учётом
 * настроек пользователя ({@link UserNotificationPreferencesModel}). Публикация не валит основную операцию: любая ошибка пишется в лог.
 *
 * <p>
 * Пробуждение ожидающих long-polling запросов происходит после коммита — иначе ожидающий мог бы перечитать БД раньше, чем запись станет
 * видимой.
 * </p>
 */
@Service
public class NotificationPublisher {

    /** Каталог событий in-app оповещений: только то, что умеет порождать код; его же выдаёт API настроек. */
    public static final Set<NotificationEvent> SUPPORTED_EVENTS = Set.of(NotificationEvent.NEW_TICKET, NotificationEvent.NEW_MESSAGE,
            NotificationEvent.NEW_SYSTEM_MESSAGE, NotificationEvent.CHANGE_TICKET, NotificationEvent.TICKET_DELETED,
            NotificationEvent.NEW_PORTAL, NotificationEvent.PORTAL_DELETED, NotificationEvent.PORTAL_TRANSFER_REQUESTED,
            NotificationEvent.PORTAL_TRANSFER_ACCEPTED, NotificationEvent.PORTAL_TRANSFER_REJECTED,
            NotificationEvent.PORTAL_TRANSFER_CANCELLED, NotificationEvent.PORTAL_TRANSFER_EXPIRED);

    /** События, которые повторяются, пока не прочитаны (см. NotificationRepeatService). */
    public static final Set<NotificationEvent> REPEATABLE_EVENTS = Set.of(NotificationEvent.NEW_TICKET, NotificationEvent.NEW_MESSAGE);

    private static final int TITLE_MAX_LENGTH = 255;

    private static final Logger logger = LoggerFactory.getLogger(NotificationPublisher.class);

    private final NotificationRepository notificationRepository;
    private final UserNotificationPreferencesRepository preferencesRepository;
    private final NotificationWaitRegistry waitRegistry;

    public NotificationPublisher(NotificationRepository notificationRepository, UserNotificationPreferencesRepository preferencesRepository,
            NotificationWaitRegistry waitRegistry) {
        this.notificationRepository = notificationRepository;
        this.preferencesRepository = preferencesRepository;
        this.waitRegistry = waitRegistry;
    }

    /** Получатели как у email: владелец портала + allowedUserIds, без автора действия (actorId). */
    public void publishToPortalUsers(PortalModel portal, NotificationEvent event, Long actorId, Long ticketId, String title) {
        Objects.requireNonNull(portal, "portal не должен быть null");

        Set<Long> recipients = new HashSet<>();
        if (portal.getOwner() != null && portal.getOwner().getId() != null) {
            recipients.add(portal.getOwner().getId());
        }
        if (portal.getAllowedUserIds() != null) {
            recipients.addAll(portal.getAllowedUserIds());
        }

        publishToUsers(recipients, event, actorId, ticketId, portal.getId(), title);
    }

    public void publishToUsers(Collection<Long> recipients, NotificationEvent event, Long actorId, Long ticketId, Long portalId,
            String title) {
        if (recipients == null || recipients.isEmpty() || event == null) {
            return;
        }
        try {
            if (!SUPPORTED_EVENTS.contains(event)) {
                logger.debug("Событие {} не входит в каталог in-app оповещений, публикация пропущена", event);
                return;
            }

            Set<Long> targets = recipients.stream().filter(Objects::nonNull).filter(userId -> !userId.equals(actorId))
                    .collect(Collectors.toSet());
            if (targets.isEmpty()) {
                return;
            }

            Map<Long, Set<NotificationEvent>> disabledByUser = disabledEvents(targets);
            String snapshotTitle = truncate(title);

            List<Long> woken = new ArrayList<>();
            for (Long userId : targets) {
                Set<NotificationEvent> disabled = disabledByUser.get(userId);
                if (disabled != null && disabled.contains(event)) {
                    continue;
                }
                notificationRepository.save(new NotificationModel(userId, event, ticketId, portalId, snapshotTitle));
                woken.add(userId);
            }

            if (!woken.isEmpty()) {
                wakeAfterCommit(woken);
            }
        } catch (Exception e) {
            // оповещение — не критичная операция: падение публикации не должно ломать создание заявки или письмо
            logger.warn("Не удалось опубликовать in-app оповещение {}, заявка {}, портал {}", event, ticketId, portalId, e);
        }
    }

    /** События, отключённые пользователем; отсутствие настроек — ничего не отключено (все события включены). */
    private Map<Long, Set<NotificationEvent>> disabledEvents(Set<Long> userIds) {
        Map<Long, Set<NotificationEvent>> disabledByUser = new HashMap<>();
        for (UserNotificationPreferencesModel preferences : preferencesRepository.findByUserIdIn(userIds)) {
            Set<NotificationEvent> disabled = new HashSet<>(SUPPORTED_EVENTS);
            disabled.removeAll(preferences.getEnabledEvents());
            disabledByUser.put(preferences.getUserId(), disabled);
        }
        return disabledByUser;
    }

    /**
     * Будит ожидающих long polling после коммита текущей транзакции — иначе ожидающий мог бы перечитать БД раньше, чем запись станет
     * видимой. Используется публикацией и повторами ({@link NotificationRepeatService}).
     */
    public void wakeAfterCommit(Collection<Long> userIds) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    userIds.forEach(waitRegistry::wake);
                }
            });
            return;
        }
        userIds.forEach(waitRegistry::wake);
    }

    private String truncate(String title) {
        if (title == null || title.length() <= TITLE_MAX_LENGTH) {
            return title;
        }
        return title.substring(0, TITLE_MAX_LENGTH);
    }
}
