package com.ukhanov.realhelpdesk.domain.attachment.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;

import com.ukhanov.realhelpdesk.core.security.user.model.UserModel;
import com.ukhanov.realhelpdesk.domain.message.model.MessageModel;

/**
 * Файл, прикреплённый к сообщению заявки. Содержимое живёт в MinIO, здесь — только метаданные и ключ объекта: строка тяжелее файла в БД не
 * хранится. Таблица append-only: вложения не редактируются (поэтому нет @Version — конфликт версий здесь бессмыслен).
 */
@Entity
public class AttachmentModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "message_id", nullable = false)
    private MessageModel message;

    /** Исходное имя файла, как его прислал пользователь (для Content-Disposition); путь из браузера вырезается при сохранении. */
    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    /** Ключ объекта в MinIO, уникален — повторная загрузка не может перезаписать чужой файл. */
    @Column(nullable = false, unique = true, length = 255)
    private String storageKey;

    @Column(name = "content_type", nullable = false, length = 255)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by", nullable = false)
    private UserModel uploadedBy;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    public void setCreatedAt() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public MessageModel getMessage() {
        return message;
    }

    public void setMessage(MessageModel message) {
        this.message = message;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public void setStorageKey(String storageKey) {
        this.storageKey = storageKey;
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

    public UserModel getUploadedBy() {
        return uploadedBy;
    }

    public void setUploadedBy(UserModel uploadedBy) {
        this.uploadedBy = uploadedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
