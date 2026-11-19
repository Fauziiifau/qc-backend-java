package com.gmp.defect_system.controller;

import com.gmp.defect_system.service.HistoryCorrectionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/history")
public class HistoryController {
    private final HistoryCorrectionService service;
    public HistoryController(HistoryCorrectionService service) { this.service = service; }

    public record ProductionCorrection(
            @NotNull LocalDate productionDate,
            @NotBlank String shift,
            @NotNull Long partId,
            String processName,
            String machineName,
            @NotNull @Min(0) Integer targetQuantity,
            @NotNull @Min(0) Integer producedQuantity,
            @NotBlank @Size(min = 5, max = 1000) String reason
    ) {}
    public record DefectCorrection(
            @NotNull LocalDate defectDate,
            String shift,
            @NotNull Long partId,
            @NotNull Long defectTypeId,
            String proses,
            String operatorName,
            @NotNull @Min(1) Integer quantity,
            String remarks,
            @NotBlank @Size(min = 5, max = 1000) String reason
    ) {}

    @GetMapping("/{type}")
    public List<Map<String,Object>> list(
            @PathVariable String type,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return service.history(type, startDate, endDate);
    }

    @GetMapping("/{type}/{id}/audit")
    public List<Map<String,Object>> audit(@PathVariable String type, @PathVariable Long id,
                                          Authentication authentication) {
        admin(authentication);
        return service.audit(type,id);
    }

    @PutMapping("/production/{id}")
    public ResponseEntity<Map<String,Object>> updateProduction(@PathVariable Long id,
                                                               @Valid @RequestBody ProductionCorrection data, Authentication authentication) {
        admin(authentication);
        service.updateProduction(id, data, authentication.getName());
        return ResponseEntity.ok(Map.of("message", "Data produksi berhasil dikoreksi"));
    }

    @PutMapping("/defect/{id}")
    public ResponseEntity<Map<String,Object>> updateDefect(@PathVariable Long id,
                                                           @Valid @RequestBody DefectCorrection data, Authentication authentication) {
        admin(authentication);
        service.updateDefect(id, data, authentication.getName());
        return ResponseEntity.ok(Map.of("message", "Data defect berhasil dikoreksi"));
    }

    private void admin(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() ||
                authentication.getAuthorities().stream().noneMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Koreksi hanya untuk Admin");
        }
    }
}