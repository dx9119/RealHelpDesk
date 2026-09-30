package com.ukhanov.realhelpdesk.feature.ticketmanager.controller;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.ukhanov.realhelpdesk.core.exception.GlobalExceptionHandler;
import com.ukhanov.realhelpdesk.core.pagination.dto.PageResponse;
import com.ukhanov.realhelpdesk.domain.ticket.model.TicketStatus;
import com.ukhanov.realhelpdesk.feature.ticketmanager.dto.CreateTicketRequest;
import com.ukhanov.realhelpdesk.feature.ticketmanager.dto.CreateTicketResponse;
import com.ukhanov.realhelpdesk.feature.ticketmanager.dto.TicketResponseOld;
import com.ukhanov.realhelpdesk.feature.ticketmanager.service.TicketManageService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("Тесты REST-контракта контроллера заявок (TicketController)")
class TicketControllerTest {

    private static final Long PORTAL_ID = 5L;
    private static final Long TICKET_ID = 77L;

    @Mock
    private TicketManageService mockTicketManageService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TicketController(mockTicketManageService))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    // ────────────────────────────────────────────────
    // Создание: 201 + Location
    // ────────────────────────────────────────────────

    @Test
    @DisplayName("POST /portals/{id}/tickets → 201 + Location на созданную заявку")
    void createTicket_returnsCreatedWithLocation() throws Exception {
        when(mockTicketManageService.createTicket(any(CreateTicketRequest.class), eq(PORTAL_ID)))
                .thenReturn(new CreateTicketResponse(TICKET_ID));

        mockMvc.perform(post("/api/v1/portals/{portalId}/tickets", PORTAL_ID).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Ошибка входа\"}")).andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/portals/5/tickets/77")).andExpect(jsonPath("$.id").value(77));
    }

    @Test
    @DisplayName("POST /portals/{id}/tickets без темы → 400 (problem+json), сервис не вызывается")
    void createTicket_withoutTitle_returnsBadRequestProblem() throws Exception {
        mockMvc.perform(
                post("/api/v1/portals/{portalId}/tickets", PORTAL_ID).contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Bad Request")).andExpect(jsonPath("$.errors.title").exists());

        verify(mockTicketManageService, never()).createTicket(any(), any());
    }

    // ────────────────────────────────────────────────
    // Обновление: PUT вместо POST
    // ────────────────────────────────────────────────

    @Test
    @DisplayName("PUT /portals/{id}/tickets/{id}/status → 204")
    void setStatus_returnsNoContent() throws Exception {
        mockMvc.perform(put("/api/v1/portals/{portalId}/tickets/{ticketId}/status", PORTAL_ID, TICKET_ID)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"CLOSED\"}")).andExpect(status().isNoContent());

        verify(mockTicketManageService).setTicketStatus(PORTAL_ID, TICKET_ID, TicketStatus.CLOSED);
    }

    @Test
    @DisplayName("PUT /portals/{id}/tickets/{id}/priority без тела → 400, сервис не вызывается")
    void setPriority_withoutBody_returnsBadRequest() throws Exception {
        mockMvc.perform(put("/api/v1/portals/{portalId}/tickets/{ticketId}/priority", PORTAL_ID, TICKET_ID)
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.priority").exists());

        verify(mockTicketManageService, never()).setTicketPriority(any(), any(), any());
    }

    @Test
    @DisplayName("POST .../set/status/{id} (старый контракт) → 404")
    void legacySetStatusPath_returnsNotFound() throws Exception {
        mockMvc.perform(post("/api/v1/portals/{portalId}/ticket/set/status/{ticketId}", PORTAL_ID, TICKET_ID).param("status", "CLOSED"))
                .andExpect(status().isNotFound());
    }

    // ────────────────────────────────────────────────
    // Удаление: DELETE + 204
    // ────────────────────────────────────────────────

    @Test
    @DisplayName("DELETE /portals/{id}/tickets/{id} → 204")
    void deleteTicket_returnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/v1/portals/{portalId}/tickets/{ticketId}", PORTAL_ID, TICKET_ID)).andExpect(status().isNoContent());

        verify(mockTicketManageService).deleteTicket(TICKET_ID, PORTAL_ID);
    }

    // ────────────────────────────────────────────────
    // Чтение: фильтры вместо отдельных путей
    // ────────────────────────────────────────────────

    @Test
    @DisplayName("GET /portals/{id}/tickets без фильтра → страница из getPageTickets")
    void getPagedTickets_withoutFilter() throws Exception {
        when(mockTicketManageService.getPageTickets(eq(PORTAL_ID), eq(0), eq(10), eq("createdAt"), eq("desc")))
                .thenReturn(new PageResponse.Builder<TicketResponseOld>().content(List.of()).page(0).size(10).totalElements(0).totalPages(0)
                        .last(true).build());

        mockMvc.perform(get("/api/v1/portals/{portalId}/tickets", PORTAL_ID)).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("GET /portals/{id}/tickets?status=OPEN → фильтр по статусу через ids")
    void getPagedTickets_withStatusFilter() throws Exception {
        when(mockTicketManageService.getIdTicketWithStatus(PORTAL_ID, TicketStatus.OPEN)).thenReturn(Set.of(1L, 2L));
        when(mockTicketManageService.getPageTicketsByIds(eq(Set.of(1L, 2L)), eq(0), eq(10), eq("createdAt"), eq("desc")))
                .thenReturn(new PageResponse.Builder<TicketResponseOld>().content(List.of()).page(0).size(10).totalElements(0).totalPages(0)
                        .last(true).build());

        mockMvc.perform(get("/api/v1/portals/{portalId}/tickets", PORTAL_ID).param("status", "OPEN")).andExpect(status().isOk());

        verify(mockTicketManageService, never()).getPageTickets(any(), anyInt(), anyInt(), any(), any());
    }

    @Test
    @DisplayName("GET /portals/{id}/tickets/ids?noAnswer=true → id без ответа")
    void getTicketIds_withoutAnswer() throws Exception {
        when(mockTicketManageService.getIdTicketNoAnswer(PORTAL_ID)).thenReturn(Set.of(9L));

        mockMvc.perform(get("/api/v1/portals/{portalId}/tickets/ids", PORTAL_ID).param("noAnswer", "true")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value(9));
    }
}
