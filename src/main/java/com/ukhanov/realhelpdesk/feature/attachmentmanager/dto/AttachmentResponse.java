package com.ukhanov.realhelpdesk.feature.attachmentmanager.dto;

import java.time.Instant;

/**
 * Метаданные вложения: всё, что нужно фронту, кроме самих байтов — их отдаёт GET downloadUrl.
 *
 * <p>
 * {@code downloadUrl} — относительный путь API или абсолютный URL с origin'ом из {@code static.base-url}, если файлы вынесены на отдельный
 * домен/ip:port (reverse-proxy/CDN).
 * </p>
 */
public record AttachmentResponse(Long id, Long messageId, Long ticketId, String fileName, String contentType, Long sizeBytes,
        String uploadedByFullName, Instant createdAt, String downloadUrl) {
}
