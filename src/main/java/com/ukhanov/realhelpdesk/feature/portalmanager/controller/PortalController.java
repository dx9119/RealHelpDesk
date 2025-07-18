package com.ukhanov.realhelpdesk.feature.portalmanager.controller;

import com.ukhanov.realhelpdesk.feature.portalmanager.dto.CreatePortalRequest;
import com.ukhanov.realhelpdesk.feature.portalmanager.dto.CreatePortalResponse;
import com.ukhanov.realhelpdesk.feature.portalmanager.dto.PortalAccessRequest;
import com.ukhanov.realhelpdesk.feature.portalmanager.dto.PortalResponse;
import com.ukhanov.realhelpdesk.feature.portalmanager.exception.PortalException;
import com.ukhanov.realhelpdesk.feature.portalmanager.service.PortalManageService;
import com.ukhanov.realhelpdesk.feature.pagination.dto.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;



@RestController
@RequestMapping("/api/v1/portals")
public class PortalController {

    private final PortalManageService portalManageService;

    public PortalController(PortalManageService portalManageService) {
        this.portalManageService = portalManageService;
    }

    @PostMapping
    public ResponseEntity<CreatePortalResponse> createPortal(
        @Valid
        @RequestBody CreatePortalRequest createPortalRequest
    ) throws PortalException {
        CreatePortalResponse response = portalManageService.createPortal(createPortalRequest);
        return ResponseEntity.ok(response);
    }

    @GetMapping()
    public PageResponse<PortalResponse> getPagedPortals(
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size,
        @RequestParam(defaultValue = "createdAt") String sortBy,
        @RequestParam(defaultValue = "desc") String order
    ) {
        return portalManageService.getPagePortalsByOwner(page, size, sortBy, order);
    }

    @GetMapping("/shared")
    public PageResponse<PortalResponse> getPagePortalsByAccess(
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size,
        @RequestParam(defaultValue = "createdAt") String sortBy,
        @RequestParam(defaultValue = "desc") String order
    ) {
        return portalManageService.getPagePortalsByAccess(page, size, sortBy, order);
    }

    @PreAuthorize("@accessValidationService.hasPortalOwner(#portalId)")
    @PostMapping("/shared/{portalId}")
    public ResponseEntity<Void> setGrantAccess(
        @PathVariable @NotNull Long portalId,
        @RequestBody @NotNull PortalAccessRequest request
    ) throws PortalException {
        portalManageService.grantUserAccessToPortal(portalId, request.getNewAccessUserId(), request.isPublic());
        return ResponseEntity.ok().build();
    }

    @PreAuthorize("@accessValidationService.hasPortalAccess(#portalId)")
    @GetMapping("/shared/{portalId}")
    public ResponseEntity<Set<UUID>> getGrantAccess(@PathVariable @NotNull Long portalId) throws PortalException {
        return ResponseEntity.ok(portalManageService.getPortalUsers(portalId));
    }

}
