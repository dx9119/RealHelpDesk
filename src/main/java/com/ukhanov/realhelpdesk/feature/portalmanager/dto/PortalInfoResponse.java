package com.ukhanov.realhelpdesk.feature.portalmanager.dto;

/**
 * Информация о портале. {@code version} — колонка {@code @Version}: клиент использует её как ETag (см. {@code core.http.EntityTag}) и как
 * признак «данные устарели» перед сохранением формы.
 */
public record PortalInfoResponse(Long id, String namePortal, String description, Integer version) {

    /** Портал без описания: у entity описание может быть null, но клиенту уходит прежнее значение по умолчанию. */
    public PortalInfoResponse(Long id, String namePortal) {
        this(id, namePortal, "none", null);
    }

    public PortalInfoResponse(Long id, String namePortal, String description) {
        this(id, namePortal, description, null);
    }
}
