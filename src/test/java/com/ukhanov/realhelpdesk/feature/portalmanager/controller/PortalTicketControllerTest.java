package com.ukhanov.realhelpdesk.feature.portalmanager.controller;

import com.ukhanov.realhelpdesk.domain.ticket.model.TicketPriority;
import com.ukhanov.realhelpdesk.domain.ticket.model.TicketStatus;
import com.ukhanov.realhelpdesk.core.exception.GlobalExceptionHandler;
import com.ukhanov.realhelpdesk.core.exception.WebValidationExceptionHandler;
import com.ukhanov.realhelpdesk.feature.ticketmanager.dto.TicketResponse;
import com.ukhanov.realhelpdesk.feature.ticketmanager.service.TicketSearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.validation.beanvalidation.MethodValidationPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("Тесты контроллера поиска заявок (PortalTicketController)")
class PortalTicketControllerTest {

    @Mock
    private TicketSearchService mockTicketSearchService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(validatedController(new PortalTicketController(mockTicketSearchService)))
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler(), new WebValidationExceptionHandler())
                .build();
    }

    /**
     * В приложении @Validated обрабатывает MethodValidationPostProcessor из ValidationAutoConfiguration.
     * В standalone-тесте контекста нет, поэтому прокси создаём тем же пост-процессором вручную.
     */
    private PortalTicketController validatedController(PortalTicketController controller) {
        MethodValidationPostProcessor processor = new MethodValidationPostProcessor();
        processor.setBeanFactory(new DefaultListableBeanFactory());
        processor.afterPropertiesSet();

        return (PortalTicketController) processor.postProcessAfterInitialization(controller, "portalTicketController");
    }

    // ────────────────────────────────────────────────
    // Проброс параметров в сервис
    // ────────────────────────────────────────────────

    @Test
    @DisplayName("search → все параметры пробрасываются в сервис как есть")
    void search_passesAllParamsToService() throws Exception {
        // given
        when(mockTicketSearchService.searchTickets(any(), any(), any(), any(), any(), anyBoolean(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));

        // when / then
        mockMvc.perform(get("/api/v1/portals/ticket/search")
                        .param("search", "битый отчёт")
                        .param("startDate", "2025-01-01T00:00:00Z")
                        .param("endDate", "2025-12-31T23:59:59Z")
                        .param("ticketStatus", "OPEN")
                        .param("ticketPriority", "HIGH")
                        .param("isMyTickets", "true")
                        .param("page", "2")
                        .param("size", "5"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);

        verify(mockTicketSearchService).searchTickets(
                eq("битый отчёт"),
                eq(Instant.parse("2025-01-01T00:00:00Z")),
                eq(Instant.parse("2025-12-31T23:59:59Z")),
                eq(TicketStatus.OPEN),
                eq(TicketPriority.HIGH),
                eq(true),
                pageableCaptor.capture()
        );

        assertThat(pageableCaptor.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(5);
    }

    @Test
    @DisplayName("search → без параметров → значения по умолчанию (null-фильтры, isMyTickets=false, page=0)")
    void search_withoutParams_usesDefaults() throws Exception {
        // given
        when(mockTicketSearchService.searchTickets(any(), any(), any(), any(), any(), anyBoolean(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));

        // when / then
        mockMvc.perform(get("/api/v1/portals/ticket/search"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);

        verify(mockTicketSearchService).searchTickets(
                isNull(), isNull(), isNull(), isNull(), isNull(),
                eq(false),
                pageableCaptor.capture()
        );

        assertThat(pageableCaptor.getValue().getPageNumber()).isZero();
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(20);
    }

    @Test
    @DisplayName("search → частично заполненные фильтры → незаполненные приходят в сервис как null")
    void search_partialFilters_passesNullsForMissing() throws Exception {
        // given
        when(mockTicketSearchService.searchTickets(any(), any(), any(), any(), any(), anyBoolean(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));

        // when / then
        mockMvc.perform(get("/api/v1/portals/ticket/search")
                        .param("search", "баг")
                        .param("ticketPriority", "CRITICAL"))
                .andExpect(status().isOk());

        verify(mockTicketSearchService).searchTickets(
                eq("баг"), isNull(), isNull(), isNull(), eq(TicketPriority.CRITICAL), eq(false), any(Pageable.class));
    }

    // ────────────────────────────────────────────────
    // Ответ
    // ────────────────────────────────────────────────

    @Test
    @DisplayName("search → возвращает страницу заявок из сервиса в JSON")
    void search_returnsServiceResultAsJson() throws Exception {
        // given
        TicketResponse ticket = new TicketResponse();
        ticket.setId(42L);
        ticket.setTitle("Ошибка входа");
        ticket.setPortalId(7L);

        Pageable pageable = PageRequest.of(0, 10);
        when(mockTicketSearchService.searchTickets(any(), any(), any(), any(), any(), anyBoolean(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(ticket), pageable, 1));

        // when / then
        mockMvc.perform(get("/api/v1/portals/ticket/search").param("search", "вход"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(42))
                .andExpect(jsonPath("$.content[0].title").value("Ошибка входа"))
                .andExpect(jsonPath("$.content[0].portalId").value(7))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("search → строка поиска длиннее 50 символов → 400, сервис не вызывается")
    void search_tooLongSearch_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/portals/ticket/search")
                        .param("search", "ы".repeat(51)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$['searchTickets.search']")
                        .value("Строка поиска не может превышать 50 символов"));

        verifyNoInteractions(mockTicketSearchService);
    }

    @Test
    @DisplayName("search → строка ровно 50 символов → 200")
    void search_maxLengthSearch_passesValidation() throws Exception {
        when(mockTicketSearchService.searchTickets(any(), any(), any(), any(), any(), anyBoolean(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));

        mockMvc.perform(get("/api/v1/portals/ticket/search")
                        .param("search", "ы".repeat(50)))
                .andExpect(status().isOk());

        verify(mockTicketSearchService).searchTickets(eq("ы".repeat(50)), isNull(), isNull(), isNull(), isNull(),
                eq(false), any(Pageable.class));
    }

    // ────────────────────────────────────────────────
    // Некорректные параметры
    // ────────────────────────────────────────────────

    @Test
    @DisplayName("search → неизвестный статус заявки → 400, сервис не вызывается")
    void search_invalidStatus_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/portals/ticket/search")
                        .param("ticketStatus", "NOT_A_STATUS"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(mockTicketSearchService);
    }

    @Test
    @DisplayName("search → неизвестный приоритет заявки → 400, сервис не вызывается")
    void search_invalidPriority_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/portals/ticket/search")
                        .param("ticketPriority", "URGENT"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(mockTicketSearchService);
    }

    @Test
    @DisplayName("search → дата в неверном формате → 400, сервис не вызывается")
    void search_invalidStartDate_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/portals/ticket/search")
                        .param("startDate", "вчера"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(mockTicketSearchService);
    }
}
