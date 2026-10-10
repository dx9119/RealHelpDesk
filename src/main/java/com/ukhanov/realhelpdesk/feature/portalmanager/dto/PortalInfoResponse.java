package com.ukhanov.realhelpdesk.feature.portalmanager.dto;

public record PortalInfoResponse(Long id, String namePortal, String description) {

    /** Портал без описания: у entity описание может быть null, но клиенту уходит прежнее значение по умолчанию. */
    public PortalInfoResponse(Long id, String namePortal) {
        this(id, namePortal, "none");
    }
}
