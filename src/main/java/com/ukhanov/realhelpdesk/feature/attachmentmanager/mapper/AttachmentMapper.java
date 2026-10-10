package com.ukhanov.realhelpdesk.feature.attachmentmanager.mapper;

import java.util.Objects;

import org.mapstruct.AfterMapping;
import org.mapstruct.BeanMapping;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

import com.ukhanov.realhelpdesk.core.config.StaticProperties;
import com.ukhanov.realhelpdesk.core.security.user.model.UserModel;
import com.ukhanov.realhelpdesk.domain.attachment.model.AttachmentModel;
import com.ukhanov.realhelpdesk.domain.message.model.MessageModel;
import com.ukhanov.realhelpdesk.feature.attachmentmanager.dto.AttachmentResponse;

/**
 * Маппинг вложений; {@code downloadUrl} собирается относительно origin'а из {@code static.base-url} (см. {@link StaticProperties}), поэтому
 * настройка передаётся в метод маппинга как {@code @Context} — бин маппера о ней ничего не знает.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface AttachmentMapper {

    /** Копируются только связь, путь и метаданные файла: id, createdAt в новой entity проставит JPA. */
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "message", source = "message")
    @Mapping(target = "uploadedBy", source = "uploadedBy")
    @Mapping(target = "fileName", source = "fileName")
    @Mapping(target = "storageKey", source = "storageKey")
    @Mapping(target = "contentType", source = "contentType")
    @Mapping(target = "sizeBytes", source = "sizeBytes")
    AttachmentModel toEntity(MessageModel message, UserModel uploadedBy, String fileName, String storageKey, String contentType,
            long sizeBytes);

    /** portalId и ticketId приходят из пути запроса: они и так проверены доступом, а в entity заявка — ленивая связь. */
    @Mapping(target = "ticketId", source = "ticketId")
    @Mapping(target = "messageId", source = "attachment.message.id")
    @Mapping(target = "uploadedByFullName", source = "attachment.uploadedBy", qualifiedByName = "authorFullName")
    @Mapping(target = "downloadUrl", ignore = true)
    AttachmentResponse toResponse(AttachmentModel attachment, Long portalId, Long ticketId, @Context StaticProperties staticProperties);

    /** «Фамилия Имя» загружающего; если автор не проставлен — так и пишем, чтобы ответ не уходил с пустым полем. */
    @Named("authorFullName")
    default String authorFullName(UserModel uploadedBy) {
        return uploadedBy != null ? uploadedBy.getLastName() + " " + uploadedBy.getFirstName() : "Неизвестный автор";
    }

    /**
     * URL файла для клиента: путь API, к которому при необходимости добавлен публичный origin раздачи ({@code static.base-url}) — так
     * клиент ходит за байтами на отдельный домен/ip:port (reverse-proxy, дальше CDN). Без настройки — относительный путь, как раньше.
     */
    default String downloadUrl(StaticProperties staticProperties, Long portalId, Long ticketId, Long attachmentId) {
        Objects.requireNonNull(staticProperties, "staticProperties");
        String path = "/api/v1/portals/" + portalId + "/tickets/" + ticketId + "/attachments/" + attachmentId;
        String baseUrl = staticProperties.getBaseUrl();
        return baseUrl != null ? baseUrl + path : path;
    }

    /** Отдельно от {@link #toResponse}: {@code downloadUrl} собирается из настройки, которой нет ни в entity, ни в пути запроса. */
    @AfterMapping
    default void fillDownloadUrl(@MappingTarget AttachmentResponse response, AttachmentModel attachment, Long portalId, Long ticketId,
            @Context StaticProperties staticProperties) {
        response.setDownloadUrl(downloadUrl(staticProperties, portalId, ticketId, attachment.getId()));
    }

}
