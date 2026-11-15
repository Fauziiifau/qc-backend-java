package com.gmp.defect_system.controller;

import com.gmp.defect_system.dto.ProductionProcessNameRequest;
import com.gmp.defect_system.dto.ProductionProcessStatusRequest;
import com.gmp.defect_system.entity.ProductionProcess;
import com.gmp.defect_system.service.ProductionProcessService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/production-processes")
public class ProductionProcessController {
    private final ProductionProcessService service;

    public ProductionProcessController(ProductionProcessService service) {
        this.service = service;
    }

    // Semua user yang login dapat membaca master proses.
    @GetMapping
    public List<ProductionProcess> listAll() {
        return service.listAll();
    }

    @GetMapping("/active")
    public List<ProductionProcess> listActive() {
        return service.listActive();
    }

    // Endpoint tulis dilindungi ROLE_ADMIN melalui WebSecurityConfig.
    @PostMapping
    public ResponseEntity<ProductionProcess> create(@Valid @RequestBody ProductionProcessNameRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.add(request.processName()));
    }

    @PutMapping("/{id}")
    public ProductionProcess rename(@PathVariable Long id,
                                    @Valid @RequestBody ProductionProcessNameRequest request) {
        return service.rename(id, request.processName());
    }

    @PatchMapping("/{id}/status")
    public ProductionProcess status(@PathVariable Long id,
                                    @Valid @RequestBody ProductionProcessStatusRequest request) {
        return service.setActive(id, request.isActive());
    }
}
