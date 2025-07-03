package com.ukhanov.RealHelpDesk.feature.portalmanager.controller;

import com.ukhanov.RealHelpDesk.feature.portalmanager.dto.CreatePortalRequest;
import com.ukhanov.RealHelpDesk.feature.portalmanager.dto.CreatePortalResponse;
import com.ukhanov.RealHelpDesk.feature.portalmanager.dto.PortalResponse;
import com.ukhanov.RealHelpDesk.feature.portalmanager.exception.PortalException;
import com.ukhanov.RealHelpDesk.feature.portalmanager.service.PortalManageService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/portals")
public class PortalController {

    private final PortalManageService portalManageService;

    public PortalController(PortalManageService portalManageService) {
        this.portalManageService = portalManageService;
    }

    @PostMapping
    public ResponseEntity<CreatePortalResponse> create(@Valid @RequestBody CreatePortalRequest createPortalRequest) throws PortalException {
        CreatePortalResponse createPortalResponse = portalManageService.createPortal(createPortalRequest);
        return ResponseEntity.status(HttpStatus.OK).body(createPortalResponse);
    }

    @GetMapping
    public ResponseEntity<List<PortalResponse>> getAll() throws PortalException {
        List<PortalResponse> allPortals = portalManageService.getAllPortals();
        return ResponseEntity.status(HttpStatus.OK).body(allPortals);
    }
}
