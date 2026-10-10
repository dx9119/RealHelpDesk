package com.ukhanov.realhelpdesk.feature.notificationmanager.service;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.async.DeferredResult;

import com.ukhanov.realhelpdesk.core.mail.model.NotificationEvent;
import com.ukhanov.realhelpdesk.core.pagination.dto.PageResponse;
import com.ukhanov.realhelpdesk.core.pagination.exception.PaginationException;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Чтение in-app оповещений (NotificationManageService)")
class NotificationManageServiceTest {

    private static final Long USER_ID = 42L;
    private static final Long OTHER_USER_ID = 43L;
    private static final Long NOTIFICATION_ID = 7L;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private UserNotificationPreferencesRepository preferencesRepository;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @Mock
    private NotificationWaitRegistry waitRegistry;

    private NotificationManageService service;

    @BeforeEach
    void setUp() {
        service = new NotificationManageService(notificationRepository, preferencesRepository, currentUserProvider, new PaginationAdapter(),
                waitRegistry, Mappers.getMapper(NotificationMapper.class));
        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
    }

    // ────────────────────────────────────────────────
    // Список и счётчик
    // ────────────────────────────────────────────────

    @Test
    @DisplayName("Список: unreadOnly=true идёт в непрочитанные, маппинг в PageResponse")
    void getNotifications_unreadOnly() {
        NotificationModel model = new NotificationModel(USER_ID, NotificationEvent.NEW_TICKET, 11L, null, "Проблема с оплатой");
        when(notificationRepository.findByRecipientIdAndReadFalse(eq(USER_ID), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(model), PageRequest.of(0, 10), 1));

        PageResponse<NotificationResponse> response = service.getNotifications(0, 10, "createdAt", "desc", true);

        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getContent().get(0).getEvent()).isEqualTo(NotificationEvent.NEW_TICKET);
        assertThat(response.getContent().get(0).getTitle()).isEqualTo("Проблема с оплатой");
        assertThat(response.getTotalElements()).isEqualTo(1);
        verify(notificationRepository, never()).findByRecipientId(eq(USER_ID), any(PageRequest.class));
    }

    @Test
    @DisplayName("Список: непрочитанные=false идёт в общий список")
    void getNotifications_all() {
        when(notificationRepository.findByRecipientId(eq(USER_ID), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));

        PageResponse<NotificationResponse> response = service.getNotifications(0, 10, "createdAt", "desc", false);

        assertThat(response.getContent()).isEmpty();
        verify(notificationRepository, never()).findByRecipientIdAndReadFalse(eq(USER_ID), any(PageRequest.class));
    }

    @Test
    @DisplayName("Список: недопустимое поле сортировки → PaginationException")
    void getNotifications_badSortField_throwsPaginationException() {
        assertThatThrownBy(() -> service.getNotifications(0, 10, "recipientId", "desc", false)).isInstanceOf(PaginationException.class);
        verify(notificationRepository, never()).findByRecipientId(any(), any(PageRequest.class));
    }

    @Test
    @DisplayName("Счётчик непрочитанных возвращает число из репозитория")
    void getUnreadCount() {
        when(notificationRepository.countByRecipientIdAndReadFalse(USER_ID)).thenReturn(5L);

        UnreadCountResponse response = service.getUnreadCount();

        assertThat(response.getCount()).isEqualTo(5L);
    }

    // ────────────────────────────────────────────────
    // Прочтение
    // ────────────────────────────────────────────────

    @Test
    @DisplayName("markRead: чужое или несуществующее уведомление → 404")
    void markRead_notOwned_throwsNotFound() {
        when(notificationRepository.findByIdAndRecipientId(NOTIFICATION_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markRead(NOTIFICATION_ID)).isInstanceOf(NotificationException.class)
                .hasMessageContaining("не найдено");
        verify(notificationRepository, never()).markGroupReadByRecipientId(anyLong(), anyLong());
    }

    @Test
    @DisplayName("markRead: непрочитанный оригинал гасит группу по его id")
    void markRead_unread_marksGroupById() throws NotificationException {
        NotificationModel model = mock(NotificationModel.class);
        when(model.getId()).thenReturn(NOTIFICATION_ID);
        when(model.getGroupId()).thenReturn(null);
        when(model.isRead()).thenReturn(false);
        when(notificationRepository.findByIdAndRecipientId(NOTIFICATION_ID, USER_ID)).thenReturn(Optional.of(model));

        service.markRead(NOTIFICATION_ID);

        verify(notificationRepository).markGroupReadByRecipientId(USER_ID, NOTIFICATION_ID);
        verify(notificationRepository, never()).save(any(NotificationModel.class));
    }

    @Test
    @DisplayName("markRead: напоминание гасит группу по group_id первоисточника")
    void markRead_reminder_marksSourceGroup() throws NotificationException {
        NotificationModel model = mock(NotificationModel.class);
        when(model.getId()).thenReturn(NOTIFICATION_ID);
        when(model.getGroupId()).thenReturn(55L);
        when(model.isRead()).thenReturn(false);
        when(notificationRepository.findByIdAndRecipientId(NOTIFICATION_ID, USER_ID)).thenReturn(Optional.of(model));

        service.markRead(NOTIFICATION_ID);

        verify(notificationRepository).markGroupReadByRecipientId(USER_ID, 55L);
    }

    @Test
    @DisplayName("markRead: уже прочитанное → группа не трогается")
    void markRead_alreadyRead_skipsUpdate() throws NotificationException {
        NotificationModel model = mock(NotificationModel.class);
        when(model.getId()).thenReturn(NOTIFICATION_ID);
        when(model.isRead()).thenReturn(true);
        when(notificationRepository.findByIdAndRecipientId(NOTIFICATION_ID, USER_ID)).thenReturn(Optional.of(model));

        service.markRead(NOTIFICATION_ID);

        verify(notificationRepository, never()).markGroupReadByRecipientId(anyLong(), anyLong());
    }

    @Test
    @DisplayName("markAllRead: массовое обновление через репозиторий")
    void markAllRead() {
        when(notificationRepository.markAllReadByRecipientId(USER_ID)).thenReturn(3);

        service.markAllRead();

        verify(notificationRepository).markAllReadByRecipientId(USER_ID);
    }

    // ────────────────────────────────────────────────
    // Настройки событий
    // ────────────────────────────────────────────────

    @Test
    @DisplayName("Настройки без сохранённой строки → все события каталога, повтор включён на 30 минут")
    void getPreferences_defaultsToAllSupportedEvents() {
        when(preferencesRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        NotificationPreferencesResponse response = service.getPreferences();

        assertThat(response.getEvents()).containsExactlyInAnyOrderElementsOf(NotificationPublisher.SUPPORTED_EVENTS);
        assertThat(response.isRepeatEnabled()).isTrue();
        assertThat(response.getRepeatIntervalMinutes()).isEqualTo(UserNotificationPreferencesModel.DEFAULT_REPEAT_INTERVAL_MINUTES);
    }

    @Test
    @DisplayName("Настройки: сохранённая строка возвращается как есть, включая повтор")
    void getPreferences_savedRowReturned() {
        UserNotificationPreferencesModel saved = new UserNotificationPreferencesModel(USER_ID, Set.of(NotificationEvent.NEW_MESSAGE));
        saved.setRepeatEnabled(false);
        saved.setRepeatIntervalMinutes(5);
        when(preferencesRepository.findByUserId(USER_ID)).thenReturn(Optional.of(saved));

        NotificationPreferencesResponse response = service.getPreferences();

        assertThat(response.getEvents()).containsExactly(NotificationEvent.NEW_MESSAGE);
        assertThat(response.isRepeatEnabled()).isFalse();
        assertThat(response.getRepeatIntervalMinutes()).isEqualTo(5);
    }

    @Test
    @DisplayName("Настройки: событие вне каталога → 400")
    void updatePreferences_unsupportedEvent_throwsBadRequest() {
        NotificationPreferencesRequest request = new NotificationPreferencesRequest(Set.of(NotificationEvent.RECOVERY_PASSWORD));

        assertThatThrownBy(() -> service.updatePreferences(request)).isInstanceOf(NotificationException.class)
                .hasMessageContaining("Неподдерживаемые события");
        verify(preferencesRepository, never()).save(any(UserNotificationPreferencesModel.class));
    }

    @Test
    @DisplayName("Настройки: без строки создаётся с дефолтами повтора, со строкой — повтор меняется")
    void updatePreferences_createsAndUpdates() throws NotificationException {
        when(preferencesRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());
        NotificationPreferencesRequest request = new NotificationPreferencesRequest(Set.of(NotificationEvent.NEW_TICKET));

        NotificationPreferencesResponse created = service.updatePreferences(request);

        assertThat(created.getEvents()).containsExactly(NotificationEvent.NEW_TICKET);
        assertThat(created.isRepeatEnabled()).isTrue();
        assertThat(created.getRepeatIntervalMinutes()).isEqualTo(UserNotificationPreferencesModel.DEFAULT_REPEAT_INTERVAL_MINUTES);
        ArgumentCaptor<UserNotificationPreferencesModel> captor = ArgumentCaptor.forClass(UserNotificationPreferencesModel.class);
        verify(preferencesRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(USER_ID);
        assertThat(captor.getValue().getEnabledEvents()).containsExactly(NotificationEvent.NEW_TICKET);
        assertThat(captor.getValue().isRepeatEnabled()).isTrue();

        UserNotificationPreferencesModel existing = new UserNotificationPreferencesModel(USER_ID, Set.of(NotificationEvent.NEW_TICKET));
        when(preferencesRepository.findByUserId(USER_ID)).thenReturn(Optional.of(existing));
        NotificationPreferencesRequest second = new NotificationPreferencesRequest(Set.of(NotificationEvent.CHANGE_TICKET), false, 5);

        NotificationPreferencesResponse updated = service.updatePreferences(second);

        assertThat(updated.getEvents()).containsExactly(NotificationEvent.CHANGE_TICKET);
        assertThat(existing.getEnabledEvents()).containsExactly(NotificationEvent.CHANGE_TICKET);
        assertThat(updated.isRepeatEnabled()).isFalse();
        assertThat(updated.getRepeatIntervalMinutes()).isEqualTo(5);
        assertThat(existing.isRepeatEnabled()).isFalse();
        assertThat(existing.getRepeatIntervalMinutes()).isEqualTo(5);
    }

    @Test
    @DisplayName("Настройки: null полей повтора не меняет сохранённые значения")
    void updatePreferences_nullRepeat_keepsCurrent() throws NotificationException {
        UserNotificationPreferencesModel existing = new UserNotificationPreferencesModel(USER_ID, Set.of(NotificationEvent.NEW_TICKET));
        existing.setRepeatEnabled(false);
        existing.setRepeatIntervalMinutes(7);
        when(preferencesRepository.findByUserId(USER_ID)).thenReturn(Optional.of(existing));

        NotificationPreferencesResponse response = service
                .updatePreferences(new NotificationPreferencesRequest(Set.of(NotificationEvent.NEW_TICKET)));

        assertThat(response.isRepeatEnabled()).isFalse();
        assertThat(response.getRepeatIntervalMinutes()).isEqualTo(7);
        assertThat(existing.isRepeatEnabled()).isFalse();
        assertThat(existing.getRepeatIntervalMinutes()).isEqualTo(7);
    }

    // ────────────────────────────────────────────────
    // Long polling
    // ────────────────────────────────────────────────

    @Test
    @DisplayName("wait: новые уже есть → ответ сразу, без регистрации в реестре")
    void waitForNew_immediateResult() {
        NotificationModel model = new NotificationModel(USER_ID, NotificationEvent.NEW_TICKET, 11L, null, "З");
        when(notificationRepository.findByRecipientIdAndIdGreaterThanOrderByIdAsc(eq(USER_ID), eq(5L), any(PageRequest.class)))
                .thenReturn(List.of(model));

        DeferredResult<ResponseEntity<PageResponse<NotificationResponse>>> deferred = service.waitForNew(5L, 25, 10);

        assertThat(deferred.hasResult()).isTrue();
        assertThat(deferred.getResult()).isInstanceOf(ResponseEntity.class);
        @SuppressWarnings("unchecked")
        ResponseEntity<PageResponse<NotificationResponse>> entity = (ResponseEntity<PageResponse<NotificationResponse>>) deferred
                .getResult();
        assertThat(entity.getBody().getContent()).hasSize(1);
        // регистрация была (защита от гонки), резолв — сразу, без пробуждения
        verify(waitRegistry).register(eq(USER_ID), any(Runnable.class));
        verify(waitRegistry, never()).wake(anyLong());
    }

    @Test
    @DisplayName("wait: новых нет → регистрация ожидателя, пробуждение отдаёт появившиеся оповещения")
    void waitForNew_waitsAndWakes() {
        NotificationModel model = new NotificationModel(USER_ID, NotificationEvent.NEW_TICKET, 11L, null, "З");
        when(notificationRepository.findByRecipientIdAndIdGreaterThanOrderByIdAsc(eq(USER_ID), eq(0L), any(PageRequest.class)))
                .thenReturn(List.of()).thenReturn(List.of(model));

        DeferredResult<ResponseEntity<PageResponse<NotificationResponse>>> deferred = service.waitForNew(0L, 25, 10);

        assertThat(deferred.hasResult()).isFalse();
        ArgumentCaptor<Runnable> onWakeCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(waitRegistry).register(eq(USER_ID), onWakeCaptor.capture());

        // имитация: публикатор будит ожидающего
        onWakeCaptor.getValue().run();

        assertThat(deferred.hasResult()).isTrue();
        @SuppressWarnings("unchecked")
        ResponseEntity<PageResponse<NotificationResponse>> entity = (ResponseEntity<PageResponse<NotificationResponse>>) deferred
                .getResult();
        assertThat(entity.getBody().getContent()).hasSize(1);
    }

    @Test
    @DisplayName("wait: пробуждение без новых строк не резолвитDeferredResult — клиент продолжает ждать")
    void waitForNew_wakeWithoutRows_keepsWaiting() {
        when(notificationRepository.findByRecipientIdAndIdGreaterThanOrderByIdAsc(eq(USER_ID), eq(0L), any(PageRequest.class)))
                .thenReturn(List.of()).thenReturn(List.of());

        DeferredResult<ResponseEntity<PageResponse<NotificationResponse>>> deferred = service.waitForNew(0L, 25, 10);

        ArgumentCaptor<Runnable> onWakeCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(waitRegistry).register(eq(USER_ID), onWakeCaptor.capture());
        onWakeCaptor.getValue().run();

        assertThat(deferred.hasResult()).isFalse();
    }

    @Test
    @DisplayName("wait: курсор afterId и размер страницы уходят в запрос")
    void waitForNew_passesCursorAndSize() {
        when(notificationRepository.findByRecipientIdAndIdGreaterThanOrderByIdAsc(eq(USER_ID), anyLong(), any(PageRequest.class)))
                .thenReturn(List.of());

        service.waitForNew(99L, 25, 5);

        verify(notificationRepository).findByRecipientIdAndIdGreaterThanOrderByIdAsc(eq(USER_ID), eq(99L), eq(PageRequest.of(0, 5)));
    }
}
