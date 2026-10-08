package com.ukhanov.realhelpdesk.domain.portal.model;

/** Жизненный цикл запроса на передачу владения порталом: активен, решён или истёк. */
public enum PortalTransferStatus {
    PENDING, ACCEPTED, REJECTED, CANCELLED, EXPIRED
}
