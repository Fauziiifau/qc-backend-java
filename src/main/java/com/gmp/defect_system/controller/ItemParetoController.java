package com.gmp.defect_system.controller;

import com.gmp.defect_system.dto.ItemParetoDto;
import com.gmp.defect_system.service.ItemParetoService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/** Endpoint tambahan; tidak mengubah endpoint Pareto lama. */
@RestController
@RequestMapping("/api/dashboard")
public class ItemParetoController {
    private final ItemParetoService itemParetoService;

    public ItemParetoController(ItemParetoService itemParetoService) {
        this.itemParetoService = itemParetoService;
    }

    @GetMapping("/pareto-items")
    public ResponseEntity<List<ItemParetoDto>> getParetoByItem(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        LocalDate start = startDate == null ? LocalDate.now().minusDays(7) : startDate;
        LocalDate end = endDate == null ? LocalDate.now() : endDate;
        if (start.isAfter(end)) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(itemParetoService.getParetoByItem(start, end));
    }
}
