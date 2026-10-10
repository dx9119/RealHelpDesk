package com.ukhanov.realhelpdesk.feature.ticketmanager.controller;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.MethodValidationPostProcessor;

import com.ukhanov.realhelpdesk.core.exception.GlobalExceptionHandler;
import com.ukhanov.realhelpdesk.core.pagination.dto.PageResponse;
import com.ukhanov.realhelpdesk.core.pagination.service.PaginationAdapter;
import com.ukhanov.realhelpdesk.domain.ticket.model.TicketPriority;
import com.ukhanov.realhelpdesk.domain.ticket.model.TicketStatus;
import com.ukhanov.realhelpdesk.feature.ticketmanager.dto.TicketResponse;
import com.ukhanov.realhelpdesk.feature.ticketmanager.dto.TicketResponseOld;
import com.ukhanov.realhelpdesk.feature.ticketmanager.service.TicketManageService;
import com.ukhanov.realhelpdesk.feature.ticketmanager.service.TicketSearchService;

import static org.assertj.core.api.Assertions.assertThat;
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

    @Mock
    private TicketManageService mockTicketManageService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        TicketSearchController controller = new TicketSearchController(mockTicketSearchService, mockTicketManageService,
                new PaginationAdapter());
        mockMvc = MockMvcBuilders.standaloneSetup(validatedController(controller))
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver()).setControllerAdvice(new GlobalExceptionHandler())
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
        mockMvc.perform(get("/api/v1/tickets").param("search", "битый отчёт").param("startDate", "2025-01-01T00:00:00Z")
                .param("endDate", "2025-12-31T23:59:59Z").param("status", "OPEN").param("priority", "HIGH").param("mine", "true")
                .param("page", "2").param("size", "5")).andExpect(status().isOk());

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);

        verify(mockTicketSearchService).searchTickets(eq("битый отчёт"), eq(Instant.parse("2025-01-01T00:00:00Z")),
                eq(Instant.parse("2025-12-31T23:59:59Z")), eq(TicketStatus.OPEN), eq(TicketPriority.HIGH), eq(true),
                pageableCaptor.capture());

        assertThat(pageableCaptor.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(5);
    }

    @Test
    @DisplayName("search → без параметров → значения по умолчанию (null-фильтры, mine=false, page=0)")
    void search_withoutParams_usesDefaults() throws Exception {
        // given
        when(mockTicketSearchService.searchTickets(any(), any(), any(), any(), any(), anyBoolean(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));

        // when / then
        mockMvc.perform(get("/api/v1/tickets")).andExpect(status().isOk());

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);

        verify(mockTicketSearchService).searchTickets(isNull(), isNull(), isNull(), isNull(), isNull(), eq(false),
                pageableCaptor.capture());

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
        mockMvc.perform(get("/api/v1/tickets").param("search", "баг").param("priority", "CRITICAL")).andExpect(status().isOk());

        verify(mockTicketSearchService).searchTickets(eq("баг"), isNull(), isNull(), isNull(), eq(TicketPriority.CRITICAL), eq(false),
                any(Pageable.class));
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
        mockMvc.perform(get("/api/v1/tickets").param("search", "вход")).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(42)).andExpect(jsonPath("$.content[0].title").value("Ошибка входа"))
                .andExpect(jsonPath("$.content[0].portalId").value(7)).andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("mine → страница заявок текущего автора")
    void mine_returnsPagedTicketsOfAuthor() throws Exception {
        TicketResponseOld ticket = new TicketResponseOld();
        ticket.setId(9L);
        ticket.setTitle("Моя заявка");

        when(mockTicketManageService.getPageTicketsByAutor(anyInt(), anyInt(), any(), any()))
                .thenReturn(new PageResponse.Builder<TicketResponseOld>().content(List.of(ticket)).page(0).size(10).totalElements(1)
                        .totalPages(1).last(true).build());

        mockMvc.perform(get("/api/v1/tickets/mine")).andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(9))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

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
        when(mockTicketSearchService.searchTickets(any(), any(), any(), any(), any(), anyBoolean(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));

        mockMvc.perform(get("/api/v1/tickets").param("search", "ы".repeat(50))).andExpect(status().isOk());

        verify(mockTicketSearchService).searchTickets(eq("ы".repeat(50)), isNull(), isNull(), isNull(), isNull(), eq(false),
                any(Pageable.class));
    }

    // ────────────────────────────────────────────────
    // Некорректные параметры
    // ────────────────────────────────────────────────

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
    void search_invalidStartDate_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/tickets").param("startDate", "вчера")).andExpect(status().isBadRequest());

        verifyNoInteractions(mockTicketSearchService);
    }
}
