package com.ukhanov.realhelpdesk.feature.portalmanager.controller;

import com.ukhanov.realhelpdesk.feature.portalmanager.dto.CreatePortalRequest;
import com.ukhanov.realhelpdesk.feature.portalmanager.dto.CreatePortalResponse;
import com.ukhanov.realhelpdesk.feature.portalmanager.dto.PortalInfoResponse;
import com.ukhanov.realhelpdesk.feature.portalmanager.dto.PortalResponse;
import com.ukhanov.realhelpdesk.feature.portalmanager.dto.PortalSetUsersRequest;
import com.ukhanov.realhelpdesk.feature.portalmanager.dto.PortalSettingsResponse;
import com.ukhanov.realhelpdesk.feature.portalmanager.exception.PortalException;
import com.ukhanov.realhelpdesk.feature.portalmanager.service.PortalManageService;
import com.ukhanov.realhelpdesk.feature.pagination.dto.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Set;
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
    @PostMapping("/shared/{portalId}/status")
    public ResponseEntity<String> setStatusPortal(
        @PathVariable @NotNull Long portalId,
        @RequestParam @NotNull boolean isPublic
    ) throws PortalException {
        portalManageService.setPortalStatus(portalId,isPublic);
        return ResponseEntity.ok("success");
    }

    @PreAuthorize("@accessValidationService.hasPortalOwner(#portalId)")
    @PostMapping("/shared/{portalId}/users")
    public ResponseEntity<String> addUsersForPortal(
        @PathVariable @NotNull Long portalId,
        @RequestBody @NotNull PortalSetUsersRequest request
    ) throws PortalException {
        portalManageService.addUserForPortal(portalId, request.getNewAccessUserId());
        return ResponseEntity.ok("success");
    }

    @PreAuthorize("@accessValidationService.hasPortalAccess(#portalId)")
    @GetMapping("/shared/{portalId}")
    public ResponseEntity<PortalSettingsResponse> getGrantAccess(@PathVariable @NotNull Long portalId) throws PortalException {
        return ResponseEntity.ok(portalManageService.getPortalSettings(portalId));
    }

    @DeleteMapping("/delete")
    public ResponseEntity<String> deletePortals(@RequestParam(name = "id") @NotNull Set<Long> idPortals) throws PortalException {
        portalManageService.deletePortals(idPortals);
        return ResponseEntity.ok("success");
    }

    @GetMapping("/info")
    public ResponseEntity<List<PortalInfoResponse>> getPortalAllInfo() throws PortalException {
        return ResponseEntity.ok(portalManageService.getAccessiblePortals());
    }

    @PreAuthorize("@accessValidationService.hasPortalAccess(#portalId)")
    @GetMapping("/info/{portalId}")
    public ResponseEntity<PortalInfoResponse> getPortalInfo(@PathVariable @NotNull Long portalId) throws PortalException {
        return ResponseEntity.ok(portalManageService.getPortalInfo(portalId));
    }

}
