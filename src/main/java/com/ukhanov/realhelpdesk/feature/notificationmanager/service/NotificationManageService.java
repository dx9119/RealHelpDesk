package com.ukhanov.realhelpdesk.feature.notificationmanager.service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.async.DeferredResult;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.ukhanov.realhelpdesk.core.mail.model.NotificationEvent;
import com.ukhanov.realhelpdesk.core.pagination.dto.PageResponse;
import com.ukhanov.realhelpdesk.core.pagination.service.PaginationAdapter;
import com.ukhanov.realhelpdesk.core.security.user.CurrentUserProvider;
import com.ukhanov.realhelpdesk.domain.notification.model.NotificationModel;
import com.ukhanov.realhelpdesk.domain.notification.model.UserNotificationPreferencesModel;
import com.ukhanov.realhelpdesk.domain.notification.repository.NotificationRepository;
import com.ukhanov.realhelpdesk.domain.notification.repository.UserNotificationPreferencesRepository;
import com.ukhanov.realhelpdesk.feature.notificationmanager.dto.NotificationPreferencesRequest;
import com.ukhanov.realhelpdesk.feature.notificationmanager.dto.NotificationPreferencesResponse;
import com.ukhanov.realhelpdesk.feature.notificationmanager.dto.NotificationResponse;
import com.ukhanov.realhelpdesk.feature.notificationmanager.dto.UnreadCountResponse;
import com.ukhanov.realhelpdesk.feature.notificationmanager.exception.NotificationException;
import com.ukhanov.realhelpdesk.feature.notificationmanager.mapper.NotificationMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Чтение in-app оповещений: список, счётчик непрочитанных, отметка о прочтении, настройки событий, SSE-канал и long polling (фолбэк).
 * Публикация — {@link NotificationPublisher}; ожидающих будит он же через {@link NotificationWaitRegistry}.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class NotificationManageService {

    private static final Set<String> SORTABLE_NOTIFICATION_FIELDS = Set.of("createdAt");
    /** Время жизни SSE-канала: EventSource переподключается сам, поэтому канал не вечный. */
    private static final Duration SSE_TIMEOUT = Duration.ofMinutes(30);

    private final NotificationRepository notificationRepository;
    private final UserNotificationPreferencesRepository preferencesRepository;
    private final CurrentUserProvider currentUserProvider;
    private final PaginationAdapter paginationAdapter;
    private final NotificationWaitRegistry waitRegistry;
    private final NotificationMapper notificationMapper;

    public PageResponse<NotificationResponse> getNotifications(int page, int size, String sortBy, String order, boolean unreadOnly) {
        Long userId = currentUserProvider.getCurrentUserId();
        PageRequest pageRequest = paginationAdapter.buildPageRequest(page, size, sortBy, order, SORTABLE_NOTIFICATION_FIELDS);

        Page<NotificationModel> notifications = unreadOnly
                ? notificationRepository.findByRecipientIdAndReadFalse(userId, pageRequest)
                : notificationRepository.findByRecipientId(userId, pageRequest);

        return paginationAdapter.mapToResponse(notifications.map(notificationMapper::toResponse), sortBy, order);
    }

    public UnreadCountResponse getUnreadCount() {
        Long userId = currentUserProvider.getCurrentUserId();
        return new UnreadCountResponse(notificationRepository.countByRecipientIdAndReadFalse(userId));
    }

    /**
     * Long polling: отвечает сразу, если есть оповещения новее курсора {@code afterId}; иначе ждёт пробуждения от публикатора до
     * {@code timeoutSec} и возвращает пустую страницу по таймауту. Клиент передаёт id последнего полученного оповещения.
     */
    public DeferredResult<ResponseEntity<PageResponse<NotificationResponse>>> waitForNew(long afterId, int timeoutSec, int size) {
        Long userId = currentUserProvider.getCurrentUserId();
        // MDC — thread-local: колбэк вызовет публикатор на своём потоке, поэтому контекст запроса
        // переносим снимком (audit/toLoggingUpdate.md, §5 п. 4).
        Map<String, String> mdcSnapshot = MDC.getCopyOfContextMap();

        DeferredResult<ResponseEntity<PageResponse<NotificationResponse>>> deferred = new DeferredResult<>(
                Duration.ofSeconds(timeoutSec).toMillis(), ResponseEntity.ok(emptyPage(size)));

        Runnable onWake = () -> {
            // пробуждение придёт на потоке публикатора или планировщика: подставляем контекст запроса,
            // который ждёт ответа, а в finally возвращаем прежний контекст будящего потока —
            // им может оказаться поток чужого HTTP-запроса, у которого своя строка access-лога
            Map<String, String> previous = MDC.getCopyOfContextMap();
            if (mdcSnapshot != null) {
                MDC.setContextMap(mdcSnapshot);
            }
            try {
                completeIfReady(deferred, userId, afterId, size);
            } finally {
                if (previous != null) {
                    MDC.setContextMap(previous);
                } else {
                    MDC.clear();
                }
            }
        };

        waitRegistry.register(userId, onWake);
        deferred.onCompletion(() -> waitRegistry.unregister(userId, onWake));
        deferred.onTimeout(() -> waitRegistry.unregister(userId, onWake));

        // уже есть новые оповещения — отвечаем сразу, оставаясь на потоке запроса (MDC уже стоит)
        completeIfReady(deferred, userId, afterId, size);
        logger.debug("Long polling оповещений: пользователь {}, afterId {}, таймаут {} сек", userId, afterId, timeoutSec);
        return deferred;
    }

    /** Общая часть ответа long polling: и немедленная проверка, и пробуждение из реестра. */
    private void completeIfReady(DeferredResult<ResponseEntity<PageResponse<NotificationResponse>>> deferred, Long userId, long afterId,
            int size) {
        if (deferred.isSetOrExpired()) {
            return;
        }
        PageResponse<NotificationResponse> fresh = fetchAfter(userId, afterId, size);
        if (!fresh.content().isEmpty()) {
            deferred.setResult(ResponseEntity.ok(fresh));
        }
    }

    /**
     * SSE-канал оповещений: одно долгоживущее соединение вместо висящего long-polling запроса (long polling остаётся фолбэком для клиентов
     * без EventSource).
     *
     * <p>
     * События отдаются как {@code event: notification} с телом {@link NotificationResponse}; курсор (id последнего отправленного
     * оповещения) ведёт подписка, поэтому повторных выдач нет. Канал живёт до таймаута или разрыва клиентом и после каждого события снова
     * встаёт в реестр ожидающих (реестр будит подписчика один раз — см. {@link NotificationWaitRegistry}).
     */
    public SseEmitter subscribe(long afterId, int size) {
        Long userId = currentUserProvider.getCurrentUserId();
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT.toMillis());
        // снимок контекста запроса: push() из реестра придёт на потоке публикатора (audit §5 п. 4b)
        NotificationStream stream = new NotificationStream(emitter, userId, afterId, size, MDC.getCopyOfContextMap());

        // Первый пакет открывает поток: клиент сразу видит соединение, а прокси — что поток живой.
        // Без него заголовки уходят только с первым оповещением, и запрос выглядит висящим.
        try {
            emitter.send(SseEmitter.event().comment("connected"));
        } catch (Exception e) {
            logger.debug("SSE-канал оповещений пользователя {} не открылся: {}", userId, e.getMessage());
            emitter.completeWithError(e);
            return emitter;
        }

        waitRegistry.register(userId, stream);
        emitter.onCompletion(stream::close);
        emitter.onTimeout(stream::close);
        emitter.onError(error -> stream.close());

        // при подключении сразу отдаём всё, что уже есть новее курсора
        stream.push();
        logger.debug("SSE-канал оповещений открыт: пользователь {}, afterId {}", userId, afterId);
        return emitter;
    }

    public void markRead(Long notificationId) throws NotificationException {
        Objects.requireNonNull(notificationId, "notificationId не должен быть null");
        Long userId = currentUserProvider.getCurrentUserId();

        NotificationModel notification = notificationRepository.findByIdAndRecipientId(notificationId, userId)
                .orElseThrow(() -> NotificationException.notFound("Уведомление не найдено"));

        if (!notification.isRead()) {
            // прочтение строки гасит всю её группу повторов: напоминания о том же событии больше не нужны
            Long groupId = notification.getGroupId() != null ? notification.getGroupId() : notification.getId();
            int updated = notificationRepository.markGroupReadByRecipientId(userId, groupId);
            logger.debug("Уведомление {} пользователя {} отмечено прочитанным вместе с группой: строк {}", notificationId, userId, updated);
        }
    }

    public void markAllRead() {
        Long userId = currentUserProvider.getCurrentUserId();
        int updated = notificationRepository.markAllReadByRecipientId(userId);
        logger.debug("Отмечено прочитанными {} уведомлений пользователя {}", updated, userId);
    }

    public NotificationPreferencesResponse getPreferences() {
        Long userId = currentUserProvider.getCurrentUserId();
        return preferencesRepository.findByUserId(userId)
                .map(preferences -> new NotificationPreferencesResponse(preferences.getEnabledEvents(), preferences.isRepeatEnabled(),
                        preferences.getRepeatIntervalMinutes()))
                .orElseGet(() -> new NotificationPreferencesResponse(NotificationPublisher.SUPPORTED_EVENTS,
                        UserNotificationPreferencesModel.DEFAULT_REPEAT_ENABLED,
                        UserNotificationPreferencesModel.DEFAULT_REPEAT_INTERVAL_MINUTES));
    }

    public NotificationPreferencesResponse updatePreferences(NotificationPreferencesRequest request) throws NotificationException {
        Objects.requireNonNull(request, "request не должен быть null");
        Long userId = currentUserProvider.getCurrentUserId();

        Set<NotificationEvent> events = request.events();
        if (!NotificationPublisher.SUPPORTED_EVENTS.containsAll(events)) {
            throw NotificationException.badRequest("Неподдерживаемые события оповещений: " + unsupported(events));
        }

        UserNotificationPreferencesModel preferences = preferencesRepository.findByUserId(userId).orElse(null);
        boolean repeatEnabled = repeatEnabled(request, preferences);
        int repeatIntervalMinutes = repeatIntervalMinutes(request, preferences);

        if (preferences == null) {
            preferences = new UserNotificationPreferencesModel(userId, events);
        }
        preferences.setEnabledEvents(events);
        preferences.setRepeatEnabled(repeatEnabled);
        preferences.setRepeatIntervalMinutes(repeatIntervalMinutes);
        preferencesRepository.save(preferences);
        logger.info("Пользователь {} обновил настройки оповещений: {} событий, повтор {} каждые {} мин", userId, events.size(),
                repeatEnabled ? "включён" : "выключен", repeatIntervalMinutes);

        return new NotificationPreferencesResponse(preferences.getEnabledEvents(), repeatEnabled, repeatIntervalMinutes);
    }

    // null в запросе — не менять: у существующей строки берётся её значение, у новой — дефолт
    private boolean repeatEnabled(NotificationPreferencesRequest request, UserNotificationPreferencesModel preferences) {
        if (request.repeatEnabled() != null) {
            return request.repeatEnabled();
        }
        return preferences != null ? preferences.isRepeatEnabled() : UserNotificationPreferencesModel.DEFAULT_REPEAT_ENABLED;
    }

    private int repeatIntervalMinutes(NotificationPreferencesRequest request, UserNotificationPreferencesModel preferences) {
        if (request.repeatIntervalMinutes() != null) {
            return request.repeatIntervalMinutes();
        }
        return preferences != null
                ? preferences.getRepeatIntervalMinutes()
                : UserNotificationPreferencesModel.DEFAULT_REPEAT_INTERVAL_MINUTES;
    }

    private PageResponse<NotificationResponse> fetchAfter(Long userId, long afterId, int size) {
        List<NotificationModel> rows = notificationRepository.findByRecipientIdAndIdGreaterThanOrderByIdAsc(userId, afterId,
                PageRequest.of(0, size));
        List<NotificationResponse> content = rows.stream().map(notificationMapper::toResponse).toList();

        return new PageResponse.Builder<NotificationResponse>().content(content).page(0).size(size).totalElements(content.size())
                .totalPages(content.isEmpty() ? 0 : 1).last(true).build();
    }

    private PageResponse<NotificationResponse> emptyPage(int size) {
        return new PageResponse.Builder<NotificationResponse>().content(List.of()).page(0).size(size).totalElements(0).totalPages(0)
                .last(true).build();
    }

    /**
     * Подписка SSE: держит курсор, отдаёт новые оповещения и после каждого пробуждения снова регистрируется в реестре —
     * {@link NotificationWaitRegistry#wake(Long)} снимает подписчиков.
     */
    private final class NotificationStream implements Runnable {

        private final SseEmitter emitter;
        private final Long userId;
        private final AtomicLong cursor;
        private final int size;
        private final Map<String, String> mdcSnapshot;
        private final AtomicBoolean closed = new AtomicBoolean();

        private NotificationStream(SseEmitter emitter, Long userId, long afterId, int size, Map<String, String> mdcSnapshot) {
            this.emitter = emitter;
            this.userId = userId;
            this.cursor = new AtomicLong(afterId);
            this.size = size;
            this.mdcSnapshot = mdcSnapshot;
        }

        @Override
        public void run() {
            // Колбэк приходит с потока публикатора/планировщика: подставляем контекст запроса,
            // который открыл канал, а в finally возвращаем прежний контекст будящего потока —
            // им может оказаться поток чужого HTTP-запроса, у которого своя строка access-лога
            Map<String, String> previous = MDC.getCopyOfContextMap();
            if (mdcSnapshot != null) {
                MDC.setContextMap(mdcSnapshot);
            }
            try {
                push();
                if (!closed.get()) {
                    waitRegistry.register(userId, this);
                }
            } finally {
                if (previous != null) {
                    MDC.setContextMap(previous);
                } else {
                    MDC.clear();
                }
            }
        }

        /** Отдаёт все оповещения новее курсора; разрыв соединения закрывает подписку. */
        void push() {
            if (closed.get()) {
                return;
            }
            try {
                for (NotificationResponse notification : fetchAfter(userId, cursor.get(), size).content()) {
                    emitter.send(SseEmitter.event().name("notification").data(notification));
                    cursor.set(Math.max(cursor.get(), notification.id()));
                }
            } catch (Exception e) {
                // Канал живёт до 30 минут, и строка access-лога появится только при закрытии соединения:
                // в prod нужно видеть, что подписка завершилась (audit/toLoggingUpdate.md, C8)
                logger.info("SSE-канал оповещений закрыт: userId={}, причина={}", userId, e.getMessage());
                close();
                emitter.completeWithError(e);
            }
        }

        void close() {
            if (closed.compareAndSet(false, true)) {
                waitRegistry.unregister(userId, this);
            }
        }
    }

    private List<String> unsupported(Set<NotificationEvent> events) {
        return events.stream().filter(event -> !NotificationPublisher.SUPPORTED_EVENTS.contains(event)).map(Enum::name).sorted().toList();
    }
}
