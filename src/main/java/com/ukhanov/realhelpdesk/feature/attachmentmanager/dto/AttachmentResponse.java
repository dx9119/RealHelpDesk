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
public class AttachmentResponse {

    private Long id;
    private Long messageId;
    private Long ticketId;
    private String fileName;
    private String contentType;
    private Long sizeBytes;
    private String uploadedByFullName;
    private Instant createdAt;
    private String downloadUrl;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getMessageId() {
        return messageId;
    }

    public void setMessageId(Long messageId) {
        this.messageId = messageId;
    }

    public Long getTicketId() {
        return ticketId;
    }

    public void setTicketId(Long ticketId) {
        this.ticketId = ticketId;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public Long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(Long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public String getUploadedByFullName() {
        return uploadedByFullName;
    }

    public void setUploadedByFullName(String uploadedByFullName) {
        this.uploadedByFullName = uploadedByFullName;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }

    public void setDownloadUrl(String downloadUrl) {
        this.downloadUrl = downloadUrl;
    }
}
