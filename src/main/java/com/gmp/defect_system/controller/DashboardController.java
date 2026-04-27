package com.gmp.defect_system.controller;

import com.gmp.defect_system.dto.DailyProductionDto;
import com.gmp.defect_system.dto.ParetoDto;
import com.gmp.defect_system.dto.KpiDto;
import com.gmp.defect_system.service.DashboardService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
@CrossOrigin(origins = "*")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }
    private LocalDate[] checkDates(LocalDate startDate, LocalDate endDate) {
        if (startDate == null) startDate = LocalDate.now().minusDays(7);
        if (endDate == null) endDate = LocalDate.now();
        return new LocalDate[]{startDate, endDate};
    }

    @GetMapping("/pareto")
    public ResponseEntity<List<ParetoDto>> getParetoData(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        LocalDate[] dates = checkDates(startDate, endDate);
        return ResponseEntity.ok(dashboardService.getParetoChartData(dates[0], dates[1]));
    }

    @GetMapping("/summary")
    public ResponseEntity<KpiDto> getSummary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        LocalDate[] dates = checkDates(startDate, endDate);
        return ResponseEntity.ok(dashboardService.getKpiSummary(dates[0], dates[1]));
    }

    @GetMapping("/daily-production")
    public ResponseEntity<List<DailyProductionDto>> getDailyProduction(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        LocalDate[] dates = checkDates(startDate, endDate);
        return ResponseEntity.ok(dashboardService.getDailyProductionData(dates[0], dates[1]));
    }

    @GetMapping("/complaints-summary")
    public ResponseEntity<Map<String, Long>> getComplaintSummary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        LocalDate[] dates = checkDates(startDate, endDate);
        long total = dashboardService.getComplaintCount(dates[0], dates[1]);
        Map<String, Long> response = new HashMap<>();
        response.put("totalComplaints", total);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/complaints-pareto")
    public ResponseEntity<List<Map<String, Object>>> getComplaintParetoData(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        LocalDate[] dates = checkDates(startDate, endDate);
        return ResponseEntity.ok(dashboardService.getComplaintParetoData(dates[0], dates[1]));
    }
}