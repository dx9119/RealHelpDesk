package com.ukhanov.realhelpdesk.feature.portalmanager.controller;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.util.List;
import java.util.Set;

import jakarta.mail.MessagingException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ukhanov.realhelpdesk.core.pagination.dto.PageResponse;
import com.ukhanov.realhelpdesk.core.security.limiter.exception.LimitException;
import com.ukhanov.realhelpdesk.feature.portalmanager.dto.CreatePortalRequest;
import com.ukhanov.realhelpdesk.feature.portalmanager.dto.CreatePortalResponse;
import com.ukhanov.realhelpdesk.feature.portalmanager.dto.DeleteResult;
import com.ukhanov.realhelpdesk.feature.portalmanager.dto.PortalInfoResponse;
import com.ukhanov.realhelpdesk.feature.portalmanager.dto.PortalResponse;
import com.ukhanov.realhelpdesk.feature.portalmanager.dto.PortalSettingsResponse;
import com.ukhanov.realhelpdesk.feature.portalmanager.dto.PortalUsersRequest;
import com.ukhanov.realhelpdesk.feature.portalmanager.dto.PortalVisibilityRequest;
import com.ukhanov.realhelpdesk.feature.portalmanager.dto.PortalVisibilityResponse;
import com.ukhanov.realhelpdesk.feature.portalmanager.dto.UpdatePortalInfoRequest;
import com.ukhanov.realhelpdesk.feature.portalmanager.exception.PortalException;
import com.ukhanov.realhelpdesk.feature.portalmanager.service.PortalManageService;
import com.ukhanov.realhelpdesk.feature.usermanager.exception.UserManageException;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/portals")
public class PortalController {

    private final PortalManageService portalManageService;

    @PostMapping
    public ResponseEntity<CreatePortalResponse> createPortal(@Valid @RequestBody CreatePortalRequest createPortalRequest)
            throws PortalException, LimitException, UserManageException, MessagingException, UnsupportedEncodingException {
        CreatePortalResponse response = portalManageService.createPortal(createPortalRequest);
        return ResponseEntity.created(URI.create("/api/v1/portals/" + response.id())).body(response);
    }

    @GetMapping
    public PageResponse<PortalResponse> getPagedPortals(@RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size, @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String order) {
        return portalManageService.getPagePortalsByOwner(page, size, sortBy, order);
    }

    @GetMapping("/shared")
    public PageResponse<PortalResponse> getPagePortalsByAccess(@RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size, @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String order) {
        return portalManageService.getPagePortalsByAccess(page, size, sortBy, order);
    }

    @GetMapping("/ids")
    public ResponseEntity<List<Long>> getPortalIds() {
        return ResponseEntity.ok(portalManageService.mapAccessiblePortalsToIds());
    }

    @GetMapping("/info")
    public ResponseEntity<List<PortalInfoResponse>> getPortalInfos() throws PortalException {
        return ResponseEntity.ok(portalManageService.mapAccessiblePortalsToInfo());
    }

    @PreAuthorize("@accessValidationService.hasPortalAccess(#portalId)")
    @GetMapping("/{portalId}")
    public ResponseEntity<PortalInfoResponse> getPortalInfo(@PathVariable @NotNull Long portalId) throws PortalException {
        return ResponseEntity.ok(portalManageService.getPortalInfo(portalId));
    }

    @PreAuthorize("@accessValidationService.hasPortalManageAccess(#portalId)")
    @PutMapping("/{portalId}")
    public ResponseEntity<PortalInfoResponse> updatePortalInfo(@PathVariable @NotNull Long portalId,
            @Valid @RequestBody UpdatePortalInfoRequest request) throws PortalException {

        PortalInfoResponse response = portalManageService.updatePortalInfo(portalId, request);
        return ResponseEntity.ok(response);
    }

    @PreAuthorize("@accessValidationService.hasPortalOwner(#portalId)")
    @GetMapping("/shared/{portalId}/visibility")
    public ResponseEntity<PortalVisibilityResponse> getPortalVisibility(@PathVariable @NotNull Long portalId) throws PortalException {
        return ResponseEntity.ok(new PortalVisibilityResponse(portalManageService.getStatusPortal(portalId)));
    }

    @PreAuthorize("@accessValidationService.hasPortalOwner(#portalId)")
    @PutMapping("/shared/{portalId}/visibility")
    public ResponseEntity<Void> setPortalVisibility(@PathVariable @NotNull Long portalId,
            @Valid @RequestBody PortalVisibilityRequest request) throws PortalException {
        portalManageService.setPortalStatus(portalId, request.isPublic());
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("@accessValidationService.hasPortalManageAccess(#portalId)")
    @GetMapping("/shared/{portalId}")
    public ResponseEntity<PortalSettingsResponse> getPortalSettings(@PathVariable @NotNull Long portalId) throws PortalException {
        return ResponseEntity.ok(portalManageService.getPortalSettings(portalId));
    }

    @PreAuthorize("@accessValidationService.hasPortalOwner(#portalId)")
    @PutMapping("/shared/{portalId}/users")
    public ResponseEntity<Void> setPortalUsers(@PathVariable @NotNull Long portalId, @Valid @RequestBody PortalUsersRequest request)
            throws PortalException, LimitException {
        portalManageService.addUserForPortal(portalId, request.userIds());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<DeleteResult> deletePortals(@RequestParam(name = "ids") @NotNull Set<Long> ids) throws PortalException {
        DeleteResult response = portalManageService.deletePortals(ids);
        return ResponseEntity.ok(response);
    }

}
