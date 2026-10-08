package com.ukhanov.realhelpdesk.feature.attachmentmanager.controller;

import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

import jakarta.mail.MessagingException;
import jakarta.validation.Valid;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import com.ukhanov.realhelpdesk.core.config.StaticProperties;
import com.ukhanov.realhelpdesk.feature.attachmentmanager.dto.AttachmentResponse;
import com.ukhanov.realhelpdesk.feature.attachmentmanager.dto.FileDownload;
import com.ukhanov.realhelpdesk.feature.attachmentmanager.dto.UploadAttachmentRequest;
import com.ukhanov.realhelpdesk.feature.attachmentmanager.exception.AttachmentException;
import com.ukhanov.realhelpdesk.feature.attachmentmanager.service.AttachmentManageService;
import com.ukhanov.realhelpdesk.feature.messagemanager.exception.MessageException;
import com.ukhanov.realhelpdesk.feature.ticketmanager.exception.TicketException;

/**
 * Файлы вложений заявок: загрузка (создаёт сообщение с файлом), список метаданных и отдача байтов фрагментами.
 *
 * <p>
 * Политика доступа — как у сообщений заявки (файл в ленте — это отдельное сообщение): загрузка, список и скачивание проверяют доступ к
 * заявке через {@code @ticketAccessValidationService.hasTicketAccess}, то же выражение, что у MessageController. Принадлежность файла
 * заявке этого портала проверяется сервисом отдельно (404).
 * </p>
 *
 * <p>
 * Отдача поддерживает HTTP Range (один диапазон): клиент на нестабильной сети (3G) докачивает файл с места обрыва запросом
 * {@code Range: bytes=<уже скачанное>-}. Фрагмент читается из MinIO только нужный — байты вне диапазона не передаются.
 * </p>
 *
 * <p>
 * URL в ответах ({@code downloadUrl}) собирается с origin'ом из {@code static.base-url}: пустая настройка — относительный путь, отдаёт API;
 * заданный домен/ip:port — клиент ходит за файлами туда, перед чем стоит reverse-proxy/CDN. Путь endpoint'а на origin'е не меняется.
 * </p>
 *
 * <p>
 * Политика кэширования задаётся {@code static.cache-control} и одинакова на всех ответах отдачи (200/206/416): дефолт
 * {@code private, no-store}; значение для CDN имеет смысл только с доступом, не завязанным на cookie.
 * </p>
 */
@RestController
@RequestMapping("/api/v1/portals/{portalId}/tickets/{ticketId}/attachments")
public class AttachmentController {

    private static final String BYTES_UNIT = "bytes";

    private final AttachmentManageService attachmentManageService;
    private final StaticProperties staticProperties;

    public AttachmentController(AttachmentManageService attachmentManageService, StaticProperties staticProperties) {
        this.attachmentManageService = attachmentManageService;
        this.staticProperties = Objects.requireNonNull(staticProperties, "staticProperties must not be null");
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@ticketAccessValidationService.hasTicketAccess(#portalId, #ticketId)")
    public ResponseEntity<AttachmentResponse> uploadAttachment(@Valid @ModelAttribute UploadAttachmentRequest form,
            @RequestParam("file") MultipartFile file, @PathVariable Long portalId, @PathVariable Long ticketId)
            throws AttachmentException, TicketException, MessageException, MessagingException, UnsupportedEncodingException {
        AttachmentResponse response = attachmentManageService.uploadAttachment(file, form.getMessageText(), portalId, ticketId);
        // Location — тот же URL, что у клиента в downloadUrl: при заданном static.base-url он абсолютный и указывает на origin раздачи.
        return ResponseEntity.created(URI.create(response.getDownloadUrl())).body(response);
    }

    @GetMapping
    @PreAuthorize("@ticketAccessValidationService.hasTicketAccess(#portalId, #ticketId)")
    public ResponseEntity<List<AttachmentResponse>> getAttachments(@PathVariable Long portalId, @PathVariable Long ticketId)
            throws AttachmentException, TicketException {
        return ResponseEntity.ok(attachmentManageService.getAttachments(portalId, ticketId));
    }

    /** Отдаёт файл потоком: без буферизации в памяти приложения и без presigned-URL — доступ всегда через JWT-cookie. */
    @GetMapping("/{attachmentId}")
    @PreAuthorize("@ticketAccessValidationService.hasTicketAccess(#portalId, #ticketId)")
    public ResponseEntity<StreamingResponseBody> downloadAttachment(@PathVariable Long portalId, @PathVariable Long ticketId,
            @PathVariable Long attachmentId, @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader,
            @RequestHeader(value = HttpHeaders.IF_RANGE, required = false) String ifRange) throws AttachmentException {
        // Валидаторов (ETag/Last-Modified) не отдаём, поэтому If-Range не может совпасть — по RFC Range в таком случае игнорируется.
        FileDownload download = attachmentManageService.downloadAttachment(portalId, ticketId, attachmentId,
                ifRange != null ? null : rangeHeader);

        if (download.isUnsatisfiable()) {
            return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE).header(HttpHeaders.ACCEPT_RANGES, BYTES_UNIT)
                    .header(HttpHeaders.CONTENT_RANGE, download.range().contentRange(download.sizeBytes()))
                    .header(HttpHeaders.CACHE_CONTROL, staticProperties.getCacheControl()).build();
        }

        boolean partial = download.isPartial();
        InputStream content = download.content();
        StreamingResponseBody body = output -> {
            try (content) {
                content.transferTo(output);
            }
        };

        ResponseEntity.BodyBuilder response = partial ? ResponseEntity.status(HttpStatus.PARTIAL_CONTENT) : ResponseEntity.ok();
        response.header(HttpHeaders.ACCEPT_RANGES, BYTES_UNIT)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(download.fileName(), StandardCharsets.UTF_8).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, staticProperties.getCacheControl()).contentType(mediaType(download.contentType()))
                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(partial ? download.range().length() : download.sizeBytes()));

        if (partial) {
            response.header(HttpHeaders.CONTENT_RANGE, download.range().contentRange(download.sizeBytes()));
        }
        return response.body(body);
    }

    private MediaType mediaType(String contentType) {
        try {
            return MediaType.parseMediaType(contentType);
        } catch (InvalidMediaTypeException e) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
