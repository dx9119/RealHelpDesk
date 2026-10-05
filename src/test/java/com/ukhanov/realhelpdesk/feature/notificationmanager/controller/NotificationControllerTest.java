package com.ukhanov.realhelpdesk.feature.notificationmanager.controller;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.MethodValidationPostProcessor;
import org.springframework.web.context.request.async.DeferredResult;

import com.ukhanov.realhelpdesk.core.exception.GlobalExceptionHandler;
import com.ukhanov.realhelpdesk.core.mail.model.NotificationEvent;
import com.ukhanov.realhelpdesk.core.pagination.dto.PageResponse;
import com.ukhanov.realhelpdesk.feature.notificationmanager.dto.NotificationPreferencesRequest;
import com.ukhanov.realhelpdesk.feature.notificationmanager.dto.NotificationPreferencesResponse;
import com.ukhanov.realhelpdesk.feature.notificationmanager.dto.NotificationResponse;
import com.ukhanov.realhelpdesk.feature.notificationmanager.dto.UnreadCountResponse;
import com.ukhanov.realhelpdesk.feature.notificationmanager.exception.NotificationException;
import com.ukhanov.realhelpdesk.feature.notificationmanager.service.NotificationManageService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("Тесты REST-контракта оповещений (NotificationController)")
class NotificationControllerTest {

    private static final Long NOTIFICATION_ID = 7L;

    @Mock
    private NotificationManageService mockNotificationManageService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        NotificationController controller = new NotificationController(mockNotificationManageService);
        mockMvc = MockMvcBuilders.standaloneSetup(validatedController(controller)).setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    /**
     * В приложении @Validated обрабатывает MethodValidationPostProcessor из ValidationAutoConfiguration. В standalone-тесте контекста нет,
     * поэтому прокси создаём тем же пост-процессором вручную (как в TicketSearchControllerTest).
     */
    private NotificationController validatedController(NotificationController controller) {
        MethodValidationPostProcessor processor = new MethodValidationPostProcessor();
        processor.setBeanFactory(new DefaultListableBeanFactory());
        processor.afterPropertiesSet();

        return (NotificationController) processor.postProcessAfterInitialization(controller, "notificationController");
    }

    // ────────────────────────────────────────────────
    // Список и счётчик
    // ────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/v1/notifications → 200 со списком оповещений")
    void getNotifications_ok() throws Exception {
        NotificationResponse response = new NotificationResponse(1L, NotificationEvent.NEW_TICKET, 11L, 2L, "Проблема с оплатой", false,
                Instant.parse("2026-10-05T10:00:00Z"));
        when(mockNotificationManageService.getNotifications(0, 10, "createdAt", "desc", false)).thenReturn(pageOf(response));

        mockMvc.perform(get("/api/v1/notifications")).andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.content[0].event").value("NEW_TICKET"))
                .andExpect(jsonPath("$.content[0].title").value("Проблема с оплатой")).andExpect(jsonPath("$.content[0].read").value(false))
                .andExpect(jsonPath("$.totalElements").value(1));

        verify(mockNotificationManageService).getNotifications(0, 10, "createdAt", "desc", false);
    }

    @Test
    @DisplayName("GET /api/v1/notifications?unreadOnly=true → проброс фильтра в сервис")
    void getNotifications_unreadOnlyParam() throws Exception {
        when(mockNotificationManageService.getNotifications(1, 5, "createdAt", "desc", true)).thenReturn(pageOf());

        mockMvc.perform(get("/api/v1/notifications").param("page", "1").param("size", "5").param("unreadOnly", "true"))
                .andExpect(status().isOk());

        verify(mockNotificationManageService).getNotifications(1, 5, "createdAt", "desc", true);
    }

    @Test
    @DisplayName("GET /api/v1/notifications?page=-1 → 400")
    void getNotifications_negativePage_badRequest() throws Exception {
        mockMvc.perform(get("/api/v1/notifications").param("page", "-1")).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /unread-count → 200 со счётчиком")
    void getUnreadCount_ok() throws Exception {
        when(mockNotificationManageService.getUnreadCount()).thenReturn(new UnreadCountResponse(3L));

        mockMvc.perform(get("/api/v1/notifications/unread-count")).andExpect(status().isOk()).andExpect(jsonPath("$.count").value(3));
    }

    // ────────────────────────────────────────────────
    // Long polling
    // ────────────────────────────────────────────────

    @Test
    @DisplayName("GET /wait → async: ответ отдаётся, когда резолвится DeferredResult")
    void waitForNew_async() throws Exception {
        DeferredResult<ResponseEntity<PageResponse<NotificationResponse>>> deferred = new DeferredResult<>();
        when(mockNotificationManageService.waitForNew(eq(5L), anyInt(), anyInt())).thenReturn(deferred);

        MvcResult mvcResult = mockMvc.perform(get("/api/v1/notifications/wait").param("afterId", "5")).andExpect(request().asyncStarted())
                .andReturn();

        NotificationResponse response = new NotificationResponse(9L, NotificationEvent.CHANGE_TICKET, 11L, 2L, "Заявка", false,
                Instant.parse("2026-10-05T10:00:00Z"));
        deferred.setResult(ResponseEntity.ok(pageOf(response)));

        mockMvc.perform(asyncDispatch(mvcResult)).andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(9))
                .andExpect(jsonPath("$.content[0].event").value("CHANGE_TICKET"));
    }

    @Test
    @DisplayName("GET /wait → пустая страница по таймауту: 200 с content=[]")
    void waitForNew_timeout_emptyPage() throws Exception {
        PageResponse<NotificationResponse> empty = pageOf();
        DeferredResult<ResponseEntity<PageResponse<NotificationResponse>>> deferred = new DeferredResult<>(25000L,
                ResponseEntity.ok(empty));
        when(mockNotificationManageService.waitForNew(anyLong(), anyInt(), anyInt())).thenReturn(deferred);

        MvcResult mvcResult = mockMvc.perform(get("/api/v1/notifications/wait")).andExpect(request().asyncStarted()).andReturn();

        // имитация таймаута: Spring выставляет timeoutValue как результат (в сервисе это пустая страница)
        deferred.setResult(ResponseEntity.ok(empty));

        mockMvc.perform(asyncDispatch(mvcResult)).andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    @DisplayName("GET /wait?timeoutSec=99 → 400 (значение вне 1..30)")
    void waitForNew_timeoutOutOfRange_badRequest() throws Exception {
        mockMvc.perform(get("/api/v1/notifications/wait").param("timeoutSec", "99")).andExpect(status().isBadRequest());
    }

    // ────────────────────────────────────────────────
    // Прочтение
    // ────────────────────────────────────────────────

    @Test
    @DisplayName("PUT /{id}/read → 204")
    void markRead_noContent() throws Exception {
        mockMvc.perform(put("/api/v1/notifications/{id}/read", NOTIFICATION_ID)).andExpect(status().isNoContent());

        verify(mockNotificationManageService).markRead(NOTIFICATION_ID);
    }

    @Test
    @DisplayName("PUT /{id}/read → уведомление не найдено → 404 problem+json")
    void markRead_notFound() throws Exception {
        doThrow(NotificationException.notFound("Уведомление не найдено")).when(mockNotificationManageService).markRead(NOTIFICATION_ID);

        mockMvc.perform(put("/api/v1/notifications/{id}/read", NOTIFICATION_ID)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("PUT /read-all → 204")
    void markAllRead_noContent() throws Exception {
        mockMvc.perform(put("/api/v1/notifications/read-all")).andExpect(status().isNoContent());

        verify(mockNotificationManageService).markAllRead();
    }

    // ────────────────────────────────────────────────
    // Настройки
    // ────────────────────────────────────────────────

    @Test
    @DisplayName("GET /preferences → 200 с событиями и настройками повтора")
    void getPreferences_ok() throws Exception {
        when(mockNotificationManageService.getPreferences())
                .thenReturn(new NotificationPreferencesResponse(Set.of(NotificationEvent.NEW_TICKET), true, 15));

        mockMvc.perform(get("/api/v1/notifications/preferences")).andExpect(status().isOk())
                .andExpect(jsonPath("$.events[0]").value("NEW_TICKET")).andExpect(jsonPath("$.repeatEnabled").value(true))
                .andExpect(jsonPath("$.repeatIntervalMinutes").value(15));
    }

    @Test
    @DisplayName("PUT /preferences → 200: события и настройки повтора возвращаются как сохранённые")
    void updatePreferences_ok() throws Exception {
        when(mockNotificationManageService.updatePreferences(any(NotificationPreferencesRequest.class)))
                .thenReturn(new NotificationPreferencesResponse(Set.of(NotificationEvent.NEW_MESSAGE), false, 5));
        String body = "{\"events\":[\"NEW_MESSAGE\"],\"repeatEnabled\":false,\"repeatIntervalMinutes\":5}";

        mockMvc.perform(put("/api/v1/notifications/preferences").contentType("application/json").content(body)).andExpect(status().isOk())
                .andExpect(jsonPath("$.events[0]").value("NEW_MESSAGE")).andExpect(jsonPath("$.repeatEnabled").value(false))
                .andExpect(jsonPath("$.repeatIntervalMinutes").value(5));
    }

    @Test
    @DisplayName("PUT /preferences → пустой список событий → 400")
    void updatePreferences_emptyEvents_badRequest() throws Exception {
        String body = "{\"events\":[]}";

        mockMvc.perform(put("/api/v1/notifications/preferences").contentType("application/json").content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT /preferences → неизвестное событие → 400")
    void updatePreferences_unknownEvent_badRequest() throws Exception {
        String body = "{\"events\":[\"SOME_UNKNOWN_EVENT\"]}";

        mockMvc.perform(put("/api/v1/notifications/preferences").contentType("application/json").content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT /preferences → интервал повтора вне 1..10080 минут → 400")
    void updatePreferences_intervalOutOfRange_badRequest() throws Exception {
        String tooSmall = "{\"events\":[\"NEW_TICKET\"],\"repeatIntervalMinutes\":0}";
        String tooLarge = "{\"events\":[\"NEW_TICKET\"],\"repeatIntervalMinutes\":10081}";

        mockMvc.perform(put("/api/v1/notifications/preferences").contentType("application/json").content(tooSmall))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/v1/notifications/preferences").contentType("application/json").content(tooLarge))
                .andExpect(status().isBadRequest());
    }

    // ────────────────────────────────────────────────

    private PageResponse<NotificationResponse> pageOf(NotificationResponse... content) {
        List<NotificationResponse> items = List.of(content);
        return new PageResponse.Builder<NotificationResponse>().content(items).page(0).size(10).totalElements(items.size())
                .totalPages(items.isEmpty() ? 0 : 1).last(true).build();
    }
}
