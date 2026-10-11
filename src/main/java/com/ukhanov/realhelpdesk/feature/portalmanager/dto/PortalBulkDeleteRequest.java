package com.ukhanov.realhelpdesk.feature.portalmanager.dto;

import java.util.Set;

import jakarta.validation.constraints.NotEmpty;

/**
 * Массовое удаление порталов: набор id приходит телом, а не query-параметром. Раньше это был {@code DELETE /portals?ids=1,2} — он смешивал
 * «удалить ресурс» и «массовую операцию» и был неотличим от удаления одного портала.
 */
public record PortalBulkDeleteRequest(@NotEmpty(message = "Список id порталов не должен быть пустым") Set<Long> ids) {
}
