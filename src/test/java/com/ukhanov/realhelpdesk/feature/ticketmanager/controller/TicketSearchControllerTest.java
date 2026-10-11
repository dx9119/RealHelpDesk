package com.ukhanov.realhelpdesk.feature.ticketmanager.controller;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.MethodValidationPostProcessor;

import com.ukhanov.realhelpdesk.core.exception.GlobalExceptionHandler;
import com.ukhanov.realhelpdesk.core.pagination.dto.PageResponse;
import com.ukhanov.realhelpdesk.domain.ticket.model.TicketPriority;
import com.ukhanov.realhelpdesk.domain.ticket.model.TicketStatus;
import com.ukhanov.realhelpdesk.feature.ticketmanager.dto.TicketResponse;
import com.ukhanov.realhelpdesk.feature.ticketmanager.service.TicketSearchService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("Тесты контроллера поиска заявок (TicketSearchController)")
class TicketSearchControllerTest {

    @Mock
    private TicketSearchService mockTicketSearchService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        TicketSearchController controller = new TicketSearchController(mockTicketSearchService);
        mockMvc = MockMvcBuilders.standaloneSetup(validatedController(controller)).setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    /**
     * В приложении @Validated обрабатывает MethodValidationPostProcessor из ValidationAutoConfiguration. В standalone-тесте контекста нет,
     * поэтому прокси создаём тем же пост-процессором вручную.
     */
    private TicketSearchController validatedController(TicketSearchController controller) {
        MethodValidationPostProcessor processor = new MethodValidationPostProcessor();
        processor.setBeanFactory(new DefaultListableBeanFactory());
        processor.afterPropertiesSet();

        return (TicketSearchController) processor.postProcessAfterInitialization(controller, "ticketSearchController");
    }

    private void stubEmptyPage() {
        when(mockTicketSearchService.searchTickets(any(), any(), any(), any(), any(), anyBoolean(), anyInt(), anyInt(), any(), any()))
                .thenReturn(new PageResponse.Builder<TicketResponse>().content(List.of()).page(0).size(10).totalElements(0).totalPages(0)
                        .last(true).build());
    }

    // ────────────────────────────────────────────────
    // Проброс параметров в сервис
    // ────────────────────────────────────────────────

    @Test
    @DisplayName("search → все параметры пробрасываются в сервис как есть")
    void search_passesAllParamsToService() throws Exception {
        // given
        stubEmptyPage();

        // when / then
        mockMvc.perform(get("/api/v1/tickets").param("search", "битый отчёт").param("startDate", "2025-01-01T00:00:00Z")
                .param("endDate", "2025-12-31T23:59:59Z").param("status", "OPEN").param("priority", "HIGH").param("mine", "true")
                .param("page", "2").param("size", "5").param("sortBy", "title").param("order", "asc")).andExpect(status().isOk());

        verify(mockTicketSearchService).searchTickets(eq("битый отчёт"), eq(Instant.parse("2025-01-01T00:00:00Z")),
                eq(Instant.parse("2025-12-31T23:59:59Z")), eq(TicketStatus.OPEN), eq(TicketPriority.HIGH), eq(true), eq(2), eq(5),
                eq("title"), eq("asc"));
    }

    @Test
    @DisplayName("search → без параметров → значения по умолчанию (null-фильтры, mine=false, page=0, size=10, createdAt desc)")
    void search_withoutParams_usesDefaults() throws Exception {
        // given
        stubEmptyPage();

        // when / then
        mockMvc.perform(get("/api/v1/tickets")).andExpect(status().isOk());

        verify(mockTicketSearchService).searchTickets(isNull(), isNull(), isNull(), isNull(), isNull(), eq(false), eq(0), eq(10),
                eq("createdAt"), eq("desc"));
    }

    @Test
    @DisplayName("search → частично заполненные фильтры → незаполненные приходят в сервис как null")
    void search_partialFilters_passesNullsForMissing() throws Exception {
        // given
        stubEmptyPage();

        // when / then
        mockMvc.perform(get("/api/v1/tickets").param("search", "баг").param("priority", "CRITICAL")).andExpect(status().isOk());

        verify(mockTicketSearchService).searchTickets(eq("баг"), isNull(), isNull(), isNull(), eq(TicketPriority.CRITICAL), eq(false),
                eq(0), eq(10), eq("createdAt"), eq("desc"));
    }

    @Test
    @DisplayName("search → mine=true → единственный способ получить «мои заявки» (эндпоинт /tickets/mine удалён)")
    void search_mineTrue_isForwardedToService() throws Exception {
        // given
        stubEmptyPage();

        // when / then
        mockMvc.perform(get("/api/v1/tickets").param("mine", "true")).andExpect(status().isOk());

        verify(mockTicketSearchService).searchTickets(isNull(), isNull(), isNull(), isNull(), isNull(), eq(true), eq(0), eq(10),
                eq("createdAt"), eq("desc"));
    }

    // ────────────────────────────────────────────────
    // Ответ
    // ────────────────────────────────────────────────

    @Test
    @DisplayName("search → возвращает страницу заявок из сервиса в JSON")
    void search_returnsServiceResultAsJson() throws Exception {
        // given
        TicketResponse ticket = new TicketResponse(42L, "Ошибка входа", "Тело", 3L, "Иванов Иван", "Портал", 7L, TicketPriority.HIGH,
                TicketStatus.OPEN, null, Instant.parse("2025-01-15T10:00:00Z"), 4);

        when(mockTicketSearchService.searchTickets(any(), any(), any(), any(), any(), anyBoolean(), anyInt(), anyInt(), any(), any()))
                .thenReturn(new PageResponse.Builder<TicketResponse>().content(List.of(ticket)).page(0).size(10).totalElements(1)
                        .totalPages(1).last(true).build());

        // when / then
        mockMvc.perform(get("/api/v1/tickets").param("search", "вход")).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(42)).andExpect(jsonPath("$.content[0].title").value("Ошибка входа"))
                .andExpect(jsonPath("$.content[0].body").value("Тело")).andExpect(jsonPath("$.content[0].authorId").value(3))
                .andExpect(jsonPath("$.content[0].portalId").value(7)).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.page").value(0));
    }

    // ────────────────────────────────────────────────
    // Некорректные параметры
    // ────────────────────────────────────────────────

    @Test
    @DisplayName("search → строка поиска длиннее 50 символов → 400, сервис не вызывается")
    void search_tooLongSearch_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/tickets").param("search", "ы".repeat(51))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors['searchTickets.search']").value("Строка поиска не может превышать 50 символов"));

        verifyNoInteractions(mockTicketSearchService);
    }

    @Test
    @DisplayName("search → строка ровно 50 символов → 200")
    void search_maxLengthSearch_passesValidation() throws Exception {
        stubEmptyPage();

        mockMvc.perform(get("/api/v1/tickets").param("search", "ы".repeat(50))).andExpect(status().isOk());

        verify(mockTicketSearchService).searchTickets(eq("ы".repeat(50)), isNull(), isNull(), isNull(), isNull(), eq(false), eq(0), eq(10),
                eq("createdAt"), eq("desc"));
    }

    @Test
    @DisplayName("search → size больше 100 → 400, сервис не вызывается (верхняя граница есть у всех списков)")
    void search_tooLargeSize_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/tickets").param("size", "1000")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400)).andExpect(jsonPath("$.errors['searchTickets.size']").exists());

        verifyNoInteractions(mockTicketSearchService);
    }

    @Test
    @DisplayName("search → неизвестный статус заявки → 400, сервис не вызывается")
    void search_invalidStatus_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/tickets").param("status", "NOT_A_STATUS")).andExpect(status().isBadRequest());

        verifyNoInteractions(mockTicketSearchService);
    }

    @Test
    @DisplayName("search → неизвестный приоритет заявки → 400, сервис не вызывается")
    void search_invalidPriority_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/tickets").param("priority", "URGENT")).andExpect(status().isBadRequest());

        verifyNoInteractions(mockTicketSearchService);
    }

    @Test
    @DisplayName("search → дата в неверном формате → 400, сервис не вызывается")
    void testSearch_invalidStartDate_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/tickets").param("startDate", "вчера")).andExpect(status().isBadRequest());

        verifyNoInteractions(mockTicketSearchService);
    }
}
