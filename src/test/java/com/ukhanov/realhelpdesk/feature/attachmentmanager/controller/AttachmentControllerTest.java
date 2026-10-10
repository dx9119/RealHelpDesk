package com.ukhanov.realhelpdesk.feature.attachmentmanager.controller;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.ukhanov.realhelpdesk.core.config.StaticProperties;
import com.ukhanov.realhelpdesk.core.exception.GlobalExceptionHandler;
import com.ukhanov.realhelpdesk.core.http.RangeHeader;
import com.ukhanov.realhelpdesk.feature.attachmentmanager.dto.AttachmentResponse;
import com.ukhanov.realhelpdesk.feature.attachmentmanager.dto.FileDownload;
import com.ukhanov.realhelpdesk.feature.attachmentmanager.exception.AttachmentException;
import com.ukhanov.realhelpdesk.feature.attachmentmanager.service.AttachmentManageService;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("Тесты REST-контракта контроллера вложений (AttachmentController)")
class AttachmentControllerTest {

    private static final Long PORTAL_ID = 5L;
    private static final Long TICKET_ID = 77L;
    private static final Long ATTACHMENT_ID = 15L;
    private static final String UPLOAD_PATH = "/api/v1/portals/{portalId}/tickets/{ticketId}/attachments";
    private static final byte[] FILE_CONTENT = "file-content".getBytes(StandardCharsets.UTF_8);

    @Mock
    private AttachmentManageService mockAttachmentManageService;

    private MockMvc mockMvc;
    private StaticProperties staticProperties;

    @BeforeEach
    void setUp() {
        staticProperties = new StaticProperties();
        mockMvc = MockMvcBuilders.standaloneSetup(new AttachmentController(mockAttachmentManageService, staticProperties))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    // ────────────────────────────────────────────────
    // Загрузка: 201 + Location
    // ────────────────────────────────────────────────

    @Test
    @DisplayName("POST .../attachments (multipart) → 201 + Location на вложение")
    void uploadAttachment_returnsCreatedWithLocation() throws Exception {
        when(mockAttachmentManageService.uploadAttachment(any(MockMultipartFile.class), eq("Вот отчёт"), eq(PORTAL_ID), eq(TICKET_ID)))
                .thenReturn(attachmentResponse());

        MockMultipartFile file = new MockMultipartFile("file", "report.pdf", MediaType.APPLICATION_PDF_VALUE, FILE_CONTENT);

        mockMvc.perform(multipart(UPLOAD_PATH, PORTAL_ID, TICKET_ID).file(file).param("messageText", "Вот отчёт"))
                .andExpect(status().isCreated()).andExpect(header().string("Location", "/api/v1/portals/5/tickets/77/attachments/15"))
                .andExpect(jsonPath("$.id").value(15)).andExpect(jsonPath("$.fileName").value("report.pdf"))
                .andExpect(jsonPath("$.sizeBytes").value(FILE_CONTENT.length))
                .andExpect(jsonPath("$.downloadUrl").value("/api/v1/portals/5/tickets/77/attachments/15"));
    }

    @Test
    @DisplayName("Location повторяет downloadUrl: при заданном static.base-url — абсолютный origin раздачи")
    void uploadAttachment_locationFollowsDownloadUrl() throws Exception {
        AttachmentResponse response = attachmentResponse("https://cdn.example.com/api/v1/portals/5/tickets/77/attachments/15");
        when(mockAttachmentManageService.uploadAttachment(any(MockMultipartFile.class), eq("Вот отчёт"), eq(PORTAL_ID), eq(TICKET_ID)))
                .thenReturn(response);

        MockMultipartFile file = new MockMultipartFile("file", "report.pdf", MediaType.APPLICATION_PDF_VALUE, FILE_CONTENT);

        mockMvc.perform(multipart(UPLOAD_PATH, PORTAL_ID, TICKET_ID).file(file).param("messageText", "Вот отчёт"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "https://cdn.example.com/api/v1/portals/5/tickets/77/attachments/15"));
    }

    @Test
    @DisplayName("POST .../attachments без части file → 400 (problem+json), сервис не вызывается")
    void uploadAttachment_withoutFile_returnsBadRequestProblem() throws Exception {
        mockMvc.perform(multipart(UPLOAD_PATH, PORTAL_ID, TICKET_ID).param("messageText", "без файла")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400)).andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.detail").value("Отсутствует обязательная часть запроса 'file'"));

        verify(mockAttachmentManageService, never()).uploadAttachment(any(), any(), any(), any());
    }

    @Test
    @DisplayName("POST .../attachments с текстом длиннее 8000 символов → 400 с ошибкой поля messageText")
    void uploadAttachment_tooLongMessageText_returnsValidationError() throws Exception {
        String longText = "а".repeat(8001);
        MockMultipartFile file = new MockMultipartFile("file", "report.pdf", MediaType.APPLICATION_PDF_VALUE, FILE_CONTENT);

        mockMvc.perform(multipart(UPLOAD_PATH, PORTAL_ID, TICKET_ID).file(file).param("messageText", longText))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.messageText").exists());

        verify(mockAttachmentManageService, never()).uploadAttachment(any(), any(), any(), any());
    }

    // ────────────────────────────────────────────────
    // Список метаданных
    // ────────────────────────────────────────────────

    @Test
    @DisplayName("GET .../attachments → 200 со списком метаданных")
    void getAttachments_returnsList() throws Exception {
        when(mockAttachmentManageService.getAttachments(PORTAL_ID, TICKET_ID)).thenReturn(List.of(attachmentResponse()));

        mockMvc.perform(get(UPLOAD_PATH, PORTAL_ID, TICKET_ID)).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(15))
                .andExpect(jsonPath("$[0].fileName").value("report.pdf")).andExpect(jsonPath("$[0].messageId").value(900));
    }

    // ────────────────────────────────────────────────
    // Отдача файла
    // ────────────────────────────────────────────────

    @Test
    @DisplayName("GET .../attachments/{id} → 200 с байтами, заголовками скачивания и Accept-Ranges")
    void downloadAttachment_returnsFileWithDownloadHeaders() throws Exception {
        when(mockAttachmentManageService.downloadAttachment(PORTAL_ID, TICKET_ID, ATTACHMENT_ID, null))
                .thenReturn(fullDownload("Отчёт за месяц.pdf", MediaType.APPLICATION_PDF_VALUE, FILE_CONTENT));

        MvcResult started = mockMvc.perform(get(UPLOAD_PATH + "/{attachmentId}", PORTAL_ID, TICKET_ID, ATTACHMENT_ID))
                .andExpect(status().isOk()).andExpect(header().string("Content-Type", MediaType.APPLICATION_PDF_VALUE))
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(header().string("Content-Disposition", containsString("filename*=UTF-8''")))
                .andExpect(header().string("Cache-Control", "private, no-store")).andExpect(header().string("Accept-Ranges", "bytes"))
                .andExpect(header().longValue("Content-Length", FILE_CONTENT.length)).andExpect(request().asyncStarted()).andReturn();

        mockMvc.perform(asyncDispatch(started)).andExpect(status().isOk()).andExpect(content().bytes(FILE_CONTENT));
    }

    @Test
    @DisplayName("Cache-Control берётся из static.cache-control: значение уходит в ответ как есть")
    void downloadAttachment_usesConfiguredCacheControl() throws Exception {
        staticProperties.setCacheControl("public, max-age=300");
        when(mockAttachmentManageService.downloadAttachment(PORTAL_ID, TICKET_ID, ATTACHMENT_ID, null))
                .thenReturn(fullDownload("Отчёт.pdf", MediaType.APPLICATION_PDF_VALUE, FILE_CONTENT));

        MvcResult started = mockMvc.perform(get(UPLOAD_PATH + "/{attachmentId}", PORTAL_ID, TICKET_ID, ATTACHMENT_ID))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "public, max-age=300"))
                .andExpect(request().asyncStarted()).andReturn();

        mockMvc.perform(asyncDispatch(started)).andExpect(status().isOk()).andExpect(content().bytes(FILE_CONTENT));
    }

    @Test
    @DisplayName("GET .../attachments/{id} c Range → 206, Content-Range и только запрошенные байты")
    void downloadAttachment_withRange_returnsPartialContent() throws Exception {
        byte[] fragment = "file-".getBytes(StandardCharsets.UTF_8);
        when(mockAttachmentManageService.downloadAttachment(PORTAL_ID, TICKET_ID, ATTACHMENT_ID, "bytes=0-4"))
                .thenReturn(partialDownload("Отчёт.pdf", MediaType.APPLICATION_PDF_VALUE, FILE_CONTENT.length, 0, 4, fragment));

        MvcResult started = mockMvc
                .perform(get(UPLOAD_PATH + "/{attachmentId}", PORTAL_ID, TICKET_ID, ATTACHMENT_ID).header("Range", "bytes=0-4"))
                .andExpect(status().isPartialContent()).andExpect(header().string("Content-Range", "bytes 0-4/" + FILE_CONTENT.length))
                .andExpect(header().string("Accept-Ranges", "bytes")).andExpect(header().longValue("Content-Length", fragment.length))
                .andExpect(request().asyncStarted()).andReturn();

        mockMvc.perform(asyncDispatch(started)).andExpect(status().isPartialContent()).andExpect(content().bytes(fragment));
    }

    @Test
    @DisplayName("GET .../attachments/{id} c Range за концом файла → 416 с Content-Range «bytes * / size», поток не открывается")
    void downloadAttachment_unsatisfiableRange_returnsRequestedRangeNotSatisfiable() throws Exception {
        when(mockAttachmentManageService.downloadAttachment(PORTAL_ID, TICKET_ID, ATTACHMENT_ID, "bytes=9999-"))
                .thenReturn(unsatisfiableDownload("Отчёт.pdf", MediaType.APPLICATION_PDF_VALUE, FILE_CONTENT.length));

        mockMvc.perform(get(UPLOAD_PATH + "/{attachmentId}", PORTAL_ID, TICKET_ID, ATTACHMENT_ID).header("Range", "bytes=9999-"))
                .andExpect(status().isRequestedRangeNotSatisfiable())
                .andExpect(header().string("Content-Range", "bytes */" + FILE_CONTENT.length))
                .andExpect(header().string("Cache-Control", "private, no-store")).andExpect(header().string("Accept-Ranges", "bytes"));
    }

    @Test
    @DisplayName("GET .../attachments/{id} c If-Range → Range игнорируется (валидаторов не отдаём), ответ 200 целиком")
    void downloadAttachment_withIfRange_ignoresRange() throws Exception {
        when(mockAttachmentManageService.downloadAttachment(PORTAL_ID, TICKET_ID, ATTACHMENT_ID, null))
                .thenReturn(fullDownload("Отчёт.pdf", MediaType.APPLICATION_PDF_VALUE, FILE_CONTENT));

        MvcResult started = mockMvc
                .perform(get(UPLOAD_PATH + "/{attachmentId}", PORTAL_ID, TICKET_ID, ATTACHMENT_ID).header("Range", "bytes=0-4")
                        .header("If-Range", "\"abc\""))
                .andExpect(status().isOk()).andExpect(header().doesNotExist("Content-Range")).andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(started)).andExpect(status().isOk()).andExpect(content().bytes(FILE_CONTENT));
        verify(mockAttachmentManageService).downloadAttachment(PORTAL_ID, TICKET_ID, ATTACHMENT_ID, null);
    }

    @Test
    @DisplayName("GET .../attachments/{id} с несколькими диапазонами → сервис отдаёт FULL, ответ 200 целиком")
    void downloadAttachment_multipleRanges_fallsBackToFullResponse() throws Exception {
        when(mockAttachmentManageService.downloadAttachment(PORTAL_ID, TICKET_ID, ATTACHMENT_ID, "bytes=0-1,5-6"))
                .thenReturn(fullDownload("Отчёт.pdf", MediaType.APPLICATION_PDF_VALUE, FILE_CONTENT));

        MvcResult started = mockMvc
                .perform(get(UPLOAD_PATH + "/{attachmentId}", PORTAL_ID, TICKET_ID, ATTACHMENT_ID).header("Range", "bytes=0-1,5-6"))
                .andExpect(status().isOk()).andExpect(header().doesNotExist("Content-Range")).andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(started)).andExpect(status().isOk()).andExpect(content().bytes(FILE_CONTENT));
    }

    @Test
    @DisplayName("GET .../attachments/{id} файла нет → 404 problem+json")
    void downloadAttachment_unknown_returnsNotFoundProblem() throws Exception {
        when(mockAttachmentManageService.downloadAttachment(PORTAL_ID, TICKET_ID, ATTACHMENT_ID, null))
                .thenThrow(AttachmentException.notFound("Файл не найден"));

        mockMvc.perform(get(UPLOAD_PATH + "/{attachmentId}", PORTAL_ID, TICKET_ID, ATTACHMENT_ID)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404)).andExpect(jsonPath("$.detail").value("Файл не найден"));
    }

    // ────────────────────────────────────────────────
    // Вспомогательные фабрики
    // ────────────────────────────────────────────────

    private FileDownload fullDownload(String fileName, String contentType, byte[] content) {
        return new FileDownload(fileName, contentType, content.length, RangeHeader.parse(null, content.length),
                new ByteArrayInputStream(content));
    }

    private FileDownload partialDownload(String fileName, String contentType, long totalSize, long start, long end, byte[] content) {
        return new FileDownload(fileName, contentType, totalSize, RangeHeader.parse("bytes=" + start + "-" + end, totalSize),
                new ByteArrayInputStream(content));
    }

    private FileDownload unsatisfiableDownload(String fileName, String contentType, long totalSize) {
        return new FileDownload(fileName, contentType, totalSize, RangeHeader.parse("bytes=9999-", totalSize), null);
    }

    private AttachmentResponse attachmentResponse() {
        return attachmentResponse("/api/v1/portals/5/tickets/77/attachments/15");
    }

    private AttachmentResponse attachmentResponse(String downloadUrl) {
        return new AttachmentResponse(ATTACHMENT_ID, 900L, TICKET_ID, "report.pdf", MediaType.APPLICATION_PDF_VALUE,
                (long) FILE_CONTENT.length, "Иванов Иван", Instant.now(), downloadUrl);
    }
}
