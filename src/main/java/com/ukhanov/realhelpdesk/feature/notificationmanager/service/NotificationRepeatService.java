package com.ukhanov.realhelpdesk.feature.notificationmanager.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ukhanov.realhelpdesk.domain.notification.model.NotificationModel;
import com.ukhanov.realhelpdesk.domain.notification.model.UserNotificationPreferencesModel;
import com.ukhanov.realhelpdesk.domain.notification.repository.NotificationRepository;
import com.ukhanov.realhelpdesk.domain.notification.repository.UserNotificationPreferencesRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Повторы непрочитанных оповещений о новых заявках и сообщениях ({@link NotificationPublisher#REPEATABLE_EVENTS}): каждые
 * {@link UserNotificationPreferencesModel#getRepeatIntervalMinutes()} минут получателю создаётся новая строка-напоминание с той же группой,
 * пока он не прочитает ни одну из них. Повторяемость и интервал — настройки пользователя; выключённое событие не повторяется.
 *
 * <p>
 * Обход раз в минуту: интервал задан в минутах, поэтому фактическая периодичность повтора — интервал плюс до одного периода обхода.
 * Пробуждение ожидающих long polling — после коммита, как у публикации.
 * </p>
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class NotificationRepeatService {

    /** За один обход не больше этого напоминаний — остальные догонят следующие запуски. */
    private static final int SCAN_BATCH_SIZE = 500;

    private static final long SCAN_PERIOD_MS = 60_000L;

    private final NotificationRepository notificationRepository;
    private final UserNotificationPreferencesRepository preferencesRepository;
    private final NotificationPublisher notificationPublisher;

    @Scheduled(fixedDelay = SCAN_PERIOD_MS, initialDelay = SCAN_PERIOD_MS)
    @Transactional
    public void repeatDueNotifications() {
        List<NotificationModel> candidates = notificationRepository.findRepeatCandidates(NotificationPublisher.REPEATABLE_EVENTS,
                PageRequest.of(0, SCAN_BATCH_SIZE, Sort.by(Sort.Direction.ASC, "createdAt")));
        if (candidates.isEmpty()) {
            return;
        }

        Set<Long> userIds = candidates.stream().map(NotificationModel::getRecipientId).collect(Collectors.toSet());
        Map<Long, UserNotificationPreferencesModel> preferencesByUser = preferencesRepository.findByUserIdIn(userIds).stream()
                .collect(Collectors.toMap(UserNotificationPreferencesModel::getUserId, preferences -> preferences, (left, right) -> left));

        Instant now = Instant.now();
        List<Long> woken = new ArrayList<>();
        for (NotificationModel source : candidates) {
            if (!repeatAllowed(source, preferencesByUser.get(source.getRecipientId()), now)) {
                continue;
            }
            woken.add(remind(source));
        }

        if (!woken.isEmpty()) {
            notificationPublisher.wakeAfterCommit(woken);
            logger.debug("Повторные оповещения созданы для {} получателей", woken.size());
        }
    }

    /** Нет строки настроек — повтор включён с интервалом по умолчанию; выключенное событие не повторяется. */
    private boolean repeatAllowed(NotificationModel source, UserNotificationPreferencesModel preferences, Instant now) {
        if (preferences == null) {
            return due(source, now, UserNotificationPreferencesModel.DEFAULT_REPEAT_INTERVAL_MINUTES);
        }
        if (!preferences.isRepeatEnabled() || !preferences.getEnabledEvents().contains(source.getEvent())) {
            return false;
        }
        return due(source, now, preferences.getRepeatIntervalMinutes());
    }

    private boolean due(NotificationModel source, Instant now, int intervalMinutes) {
        return !source.getCreatedAt().isAfter(now.minus(Duration.ofMinutes(intervalMinutes)));
    }

    /** Создаёт строку-напоминание в группе источника и возвращает получателя для пробуждения. */
    private Long remind(NotificationModel source) {
        Long groupId = source.getGroupId() != null ? source.getGroupId() : source.getId();
        notificationRepository.save(new NotificationModel(source.getRecipientId(), source.getEvent(), source.getTicketId(),
                source.getPortalId(), source.getTitle(), groupId));
        return source.getRecipientId();
    }
}
