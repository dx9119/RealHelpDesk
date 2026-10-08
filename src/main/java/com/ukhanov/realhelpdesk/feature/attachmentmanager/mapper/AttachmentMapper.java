package com.ukhanov.realhelpdesk.feature.attachmentmanager.mapper;

import java.util.Objects;

import org.springframework.stereotype.Component;

import com.ukhanov.realhelpdesk.core.config.StaticProperties;
import com.ukhanov.realhelpdesk.core.security.user.model.UserModel;
import com.ukhanov.realhelpdesk.domain.attachment.model.AttachmentModel;
import com.ukhanov.realhelpdesk.domain.message.model.MessageModel;
import com.ukhanov.realhelpdesk.feature.attachmentmanager.dto.AttachmentResponse;

/** Маппинг вложений; {@code downloadUrl} собирается относительно origin'а из {@code static.base-url} (см. {@link StaticProperties}). */
@Component
public class AttachmentMapper {

    private final StaticProperties staticProperties;

    public AttachmentMapper(StaticProperties staticProperties) {
        this.staticProperties = Objects.requireNonNull(staticProperties, "staticProperties must not be null");
    }

    public AttachmentModel toEntity(MessageModel message, UserModel uploadedBy, String fileName, String storageKey, String contentType,
            long sizeBytes) {
        Objects.requireNonNull(message, "Сообщение не должно быть null");
        Objects.requireNonNull(uploadedBy, "Загружающий пользователь не должен быть null");

        AttachmentModel attachment = new AttachmentModel();
        attachment.setMessage(message);
        attachment.setUploadedBy(uploadedBy);
        attachment.setFileName(fileName);
        attachment.setStorageKey(storageKey);
        attachment.setContentType(contentType);
        attachment.setSizeBytes(sizeBytes);
        return attachment;
    }

    /** portalId и ticketId приходят из пути запроса: они и так проверены доступом, а в entity заявка — ленивая связь. */
    public AttachmentResponse toResponse(AttachmentModel attachment, Long portalId, Long ticketId) {
        Objects.requireNonNull(attachment, "Вложение не должно быть null");

        AttachmentResponse response = new AttachmentResponse();
        response.setId(attachment.getId());
        response.setTicketId(ticketId);
        response.setFileName(attachment.getFileName());
        response.setContentType(attachment.getContentType());
        response.setSizeBytes(attachment.getSizeBytes());
        response.setCreatedAt(attachment.getCreatedAt());
        response.setMessageId(attachment.getMessage() != null ? attachment.getMessage().getId() : null);
        response.setUploadedByFullName(attachment.getUploadedBy() != null
                ? attachment.getUploadedBy().getLastName() + " " + attachment.getUploadedBy().getFirstName()
                : "Неизвестный автор");
        response.setDownloadUrl(downloadUrl(portalId, ticketId, attachment.getId()));
        return response;
    }

    /**
     * URL файла для клиента: путь API, к которому при необходимости добавлен публичный origin раздачи ({@code static.base-url}) — так
     * клиент ходит за байтами на отдельный домен/ip:port (reverse-proxy, дальше CDN). Без настройки — относительный путь, как раньше.
     */
    public String downloadUrl(Long portalId, Long ticketId, Long attachmentId) {
        String path = "/api/v1/portals/" + portalId + "/tickets/" + ticketId + "/attachments/" + attachmentId;
        String baseUrl = staticProperties.getBaseUrl();
        return baseUrl != null ? baseUrl + path : path;
    }
}
