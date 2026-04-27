package com.gmp.defect_system.controller;

import com.gmp.defect_system.dto.BatchProductionRequest;
import com.gmp.defect_system.entity.Production;
import com.gmp.defect_system.service.ProductionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/production")
@CrossOrigin(origins = "*")
public class ProductionController {

    private final ProductionService productionService;

    public ProductionController(ProductionService productionService) {
        this.productionService = productionService;
    }
    @GetMapping
    public ResponseEntity<List<Production>> getAll() {
        return ResponseEntity.ok(productionService.getAll());
    }

    @PostMapping
    public ResponseEntity<Production> create(@RequestBody Production production) {
        return new ResponseEntity<>(productionService.save(production), HttpStatus.CREATED);
    }
    @PostMapping("/batch")
    public ResponseEntity<?> saveBatch(@RequestBody BatchProductionRequest request) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String username = (auth != null && auth.isAuthenticated()) ? auth.getName() : "System";

            productionService.saveBatchProduction(request, username);
            return ResponseEntity.ok(Map.of("message", "Data produksi berhasil disimpan!"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", "Gagal menyimpan data: " + e.getMessage()));
        }
    }
}