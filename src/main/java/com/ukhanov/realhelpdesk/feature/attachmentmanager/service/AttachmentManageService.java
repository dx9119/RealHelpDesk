package com.ukhanov.realhelpdesk.feature.attachmentmanager.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

import jakarta.mail.MessagingException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.ukhanov.realhelpdesk.core.config.StaticProperties;
import com.ukhanov.realhelpdesk.core.exception.ApiException;
import com.ukhanov.realhelpdesk.core.http.RangeHeader;
import com.ukhanov.realhelpdesk.core.security.user.CurrentUserProvider;
import com.ukhanov.realhelpdesk.core.storage.service.MinioStorageService;
import com.ukhanov.realhelpdesk.domain.attachment.model.AttachmentModel;
import com.ukhanov.realhelpdesk.domain.attachment.service.AttachmentDomainService;
import com.ukhanov.realhelpdesk.domain.message.model.MessageModel;
import com.ukhanov.realhelpdesk.domain.message.service.MessageDomainService;
import com.ukhanov.realhelpdesk.domain.ticket.model.TicketModel;
import com.ukhanov.realhelpdesk.domain.ticket.service.TicketDomainService;
import com.ukhanov.realhelpdesk.feature.attachmentmanager.dto.AttachmentResponse;
import com.ukhanov.realhelpdesk.feature.attachmentmanager.dto.FileDownload;
import com.ukhanov.realhelpdesk.feature.attachmentmanager.exception.AttachmentException;
import com.ukhanov.realhelpdesk.feature.attachmentmanager.mapper.AttachmentMapper;
import com.ukhanov.realhelpdesk.feature.messagemanager.dto.CreateMessageRequest;
import com.ukhanov.realhelpdesk.feature.messagemanager.dto.CreateMessageResponse;
import com.ukhanov.realhelpdesk.feature.messagemanager.exception.MessageException;
import com.ukhanov.realhelpdesk.feature.messagemanager.service.MessageManageService;
import com.ukhanov.realhelpdesk.feature.ticketmanager.exception.TicketException;

/**
 * Вложения заявок: файл живёт в MinIO, в БД — метаданные и ключ объекта.
 *
 * <p>
 * Загрузка атомарна по отношению к сообщению: один запрос создаёт сообщение в заявке (с оповещениями — как любое сообщение) и вложение к
 * нему, поэтому «файл — отдельное сообщение» остаётся верным без отдельного вызова API. Порядок операций: сначала файл в хранилище, потом
 * БД; если запись в БД не удалась, объект в MinIO удаляется — иначе в бакете остался бы осиротевший файл. Обратный порядок оставил бы
 * сообщение без файла, на которое пользователя уже уведомили.
 * </p>
 *
 * <p>
 * Чтение: политика такая же, как у сообщений заявки (файл в ленте заявки — это сообщение): контроллер проверяет доступ к заявке через
 * {@code @ticketAccessValidationService.hasTicketAccess}, а принадлежность файла заявке и заявка — порталу дополнительно проверяется
 * запросом к БД (404, а не 403, чтобы не подтверждать существование чужих файлов).
 * </p>
 */
@Service
public class AttachmentManageService {

    private static final Logger logger = LoggerFactory.getLogger(AttachmentManageService.class);

    private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";
    private static final String DEFAULT_FILE_NAME = "file";
    private static final String FILE_MESSAGE_PREFIX = "Файл: ";
    private static final String STORAGE_FOLDER = "tickets";
    private static final int MAX_FILE_NAME_LENGTH = 255;
    private static final int MAX_MESSAGE_LENGTH = 8000;
    /** MIME-тип проверяется по токену RFC 2045: что прислал клиент, не должно попасть в заголовок ответа как есть. */
    private static final String CONTENT_TYPE_PATTERN = "[\\w!#$&^_.+-]+/[\\w!#$&^_.+-]+";

    private final MinioStorageService storageService;
    private final AttachmentDomainService attachmentDomainService;
    private final MessageDomainService messageDomainService;
    private final MessageManageService messageManageService;
    private final TicketDomainService ticketDomainService;
    private final CurrentUserProvider currentUserProvider;
    private final AttachmentMapper attachmentMapper;
    private final StaticProperties staticProperties;

    public AttachmentManageService(MinioStorageService storageService, AttachmentDomainService attachmentDomainService,
            MessageDomainService messageDomainService, MessageManageService messageManageService, TicketDomainService ticketDomainService,
            CurrentUserProvider currentUserProvider, AttachmentMapper attachmentMapper, StaticProperties staticProperties) {
        this.storageService = Objects.requireNonNull(storageService, "storageService must not be null");
        this.attachmentDomainService = Objects.requireNonNull(attachmentDomainService, "attachmentDomainService must not be null");
        this.messageDomainService = Objects.requireNonNull(messageDomainService, "messageDomainService must not be null");
        this.messageManageService = Objects.requireNonNull(messageManageService, "messageManageService must not be null");
        this.ticketDomainService = Objects.requireNonNull(ticketDomainService, "ticketDomainService must not be null");
        this.currentUserProvider = Objects.requireNonNull(currentUserProvider, "currentUserProvider must not be null");
        this.attachmentMapper = Objects.requireNonNull(attachmentMapper, "attachmentMapper must not be null");
        this.staticProperties = Objects.requireNonNull(staticProperties, "staticProperties must not be null");
    }

    public AttachmentResponse uploadAttachment(MultipartFile file, String messageText, Long portalId, Long ticketId)
            throws AttachmentException, TicketException, MessageException, MessagingException, UnsupportedEncodingException {
        Objects.requireNonNull(file, "Файл не должен быть null");
        Objects.requireNonNull(portalId, "ID портала не должен быть null");
        Objects.requireNonNull(ticketId, "ID заявки не должен быть null");

        if (file.isEmpty()) {
            throw AttachmentException.badRequest("Файл не может быть пустым");
        }
        requireTicketInPortal(ticketId, portalId);

        String fileName = sanitizeFileName(file.getOriginalFilename());
        String contentType = resolveContentType(file.getContentType());
        long sizeBytes = file.getSize();
        String storageKey = storageKey(ticketId, fileName);

        try (InputStream content = file.getInputStream()) {
            storageService.upload(storageKey, content, sizeBytes, contentType);
        } catch (IOException e) {
            logger.warn("Не удалось прочитать загружаемый файл «{}» для заявки {}", fileName, ticketId, e);
            throw new AttachmentException("Не удалось прочитать файл", HttpStatus.BAD_REQUEST, e);
        }

        try {
            CreateMessageRequest request = new CreateMessageRequest();
            request.setMessageText(resolveMessageText(messageText, fileName));
            CreateMessageResponse createdMessage = messageManageService.createMessage(request, ticketId, portalId);
            MessageModel message = messageDomainService.getMessageById(createdMessage.getId());

            AttachmentModel attachment = attachmentDomainService.saveAttachment(attachmentMapper.toEntity(message,
                    currentUserProvider.getCurrentUserModel(), fileName, storageKey, contentType, sizeBytes));

            logger.info("Файл «{}» ({} байт) прикреплён к заявке {} сообщением {}", fileName, sizeBytes, ticketId, message.getId());
            return attachmentMapper.toResponse(attachment, portalId, ticketId, staticProperties);
        } catch (ApiException | MessagingException | UnsupportedEncodingException | RuntimeException e) {
            storageService.delete(storageKey);
            throw e;
        }
    }

    public List<AttachmentResponse> getAttachments(Long portalId, Long ticketId) throws AttachmentException, TicketException {
        Objects.requireNonNull(portalId, "ID портала не должен быть null");
        Objects.requireNonNull(ticketId, "ID заявки не должен быть null");

        requireTicketInPortal(ticketId, portalId);

        return attachmentDomainService.getAttachmentsForTicket(ticketId, portalId).stream()
                .map(attachment -> attachmentMapper.toResponse(attachment, portalId, ticketId, staticProperties)).toList();
    }

    /**
     * Поток из MinIO открывается здесь и живёт до закрытия контроллером — иначе соединение с хранилищем утечёт на каждый скачанный файл.
     *
     * @param rangeHeader
     *            значение заголовка Range (null — весь файл). Диапазон разрешается по метаданным из БД до открытия потока, поэтому при
     *            неудовлетворимом диапазоне (416) соединение с хранилищем вообще не устанавливается
     */
    public FileDownload downloadAttachment(Long portalId, Long ticketId, Long attachmentId, String rangeHeader) throws AttachmentException {
        Objects.requireNonNull(portalId, "ID портала не должен быть null");
        Objects.requireNonNull(ticketId, "ID заявки не должен быть null");
        Objects.requireNonNull(attachmentId, "ID вложения не должен быть null");

        AttachmentModel attachment = attachmentDomainService.getAttachmentForTicket(attachmentId, ticketId, portalId);
        RangeHeader.Parsed range = RangeHeader.parse(rangeHeader, attachment.getSizeBytes());

        InputStream content;
        if (range.kind() == RangeHeader.Kind.UNSATISFIABLE) {
            content = null;
        } else if (range.kind() == RangeHeader.Kind.PARTIAL) {
            content = storageService.download(attachment.getStorageKey(), range.start(), range.length());
        } else {
            content = storageService.download(attachment.getStorageKey());
        }
        return new FileDownload(attachment.getFileName(), attachment.getContentType(), attachment.getSizeBytes(), range, content);
    }

    /** Заявка обязана существовать (не удалена) и принадлежать порталу из пути — иначе 404, как в остальных endpoint'ах заявок. */
    private void requireTicketInPortal(Long ticketId, Long portalId) throws TicketException {
        TicketModel ticket = ticketDomainService.findTicketById(ticketId);
        if (ticket.getPortal() == null || !portalId.equals(ticket.getPortal().getId())) {
            throw TicketException.notFound("Заявка не найдена!");
        }
    }

    /** Пустой текст заменяется на подпись с именем файла: у вложения всегда есть осмысленное сообщение в ленте заявки. */
    private String resolveMessageText(String messageText, String fileName) throws AttachmentException {
        String text = messageText == null || messageText.isBlank() ? FILE_MESSAGE_PREFIX + fileName : messageText.trim();
        if (text.length() > MAX_MESSAGE_LENGTH) {
            throw AttachmentException.badRequest("Сообщение не может быть больше " + MAX_MESSAGE_LENGTH + " символов");
        }
        return text;
    }

    /** Оставляет только имя: браузер шлёт C:\\fakepath\\..., а длинное имя урезается с начала, чтобы сохранить расширение. */
    private String sanitizeFileName(String originalFileName) {
        String name = originalFileName == null ? "" : originalFileName.trim();
        int separator = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (separator >= 0) {
            name = name.substring(separator + 1);
        }
        name = name.replaceAll("[\\p{Cntrl}]", "").trim();
        if (name.isEmpty()) {
            name = DEFAULT_FILE_NAME;
        }
        if (name.length() > MAX_FILE_NAME_LENGTH) {
            name = name.substring(name.length() - MAX_FILE_NAME_LENGTH);
        }
        return name;
    }

    private String resolveContentType(String contentType) {
        if (contentType == null || contentType.isBlank() || contentType.length() > MAX_FILE_NAME_LENGTH
                || !contentType.trim().matches(CONTENT_TYPE_PATTERN)) {
            return DEFAULT_CONTENT_TYPE;
        }
        return contentType.trim();
    }

    /** Ключ строится до загрузки и не зависит от содержимого: UUID исключает перезапись, а расширение — только безопасные символы. */
    private String storageKey(Long ticketId, String fileName) {
        return STORAGE_FOLDER + "/" + ticketId + "/" + UUID.randomUUID() + extensionOf(fileName);
    }

    private String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot <= 0 || dot == fileName.length() - 1) {
            return "";
        }
        String extension = fileName.substring(dot).toLowerCase(Locale.ROOT);
        return extension.matches("\\.[a-z0-9]{1,15}") ? extension : "";
    }
}
