package com.gmp.defect_system.controller;

import com.gmp.defect_system.service.PhotoReportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;

/** Endpoint terpisah untuk export dengan bukti foto.
 *  Endpoint /api/reports/download-* yang lama tidak diubah.
 */
@RestController
@RequestMapping("/api/reports/v2")
public class PhotoReportController {
    private final PhotoReportService reportService;

    public PhotoReportController(PhotoReportService reportService) {
        this.reportService = reportService;
    }

    private void checkDateRange(LocalDate start, LocalDate end) {
        if (start != null && end != null && start.isAfter(end)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Tanggal mulai tidak boleh melebihi tanggal akhir.");
        }
    }

    @GetMapping("/download-ng-data")
    public ResponseEntity<String> downloadNgDataReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "true") boolean includePhotos) {
        checkDateRange(startDate, endDate);
        String csv = reportService.generateNgDataCsv(startDate, endDate, includePhotos);
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=Laporan_QC_Defect.csv");
        headers.add(HttpHeaders.CONTENT_TYPE, "text/csv; charset=UTF-8");
        return new ResponseEntity<>(csv, headers, HttpStatus.OK);
    }

    @GetMapping("/download-ng-data-pdf")
    public ResponseEntity<byte[]> downloadNgDataReportPdf(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "true") boolean includePhotos) {
        checkDateRange(startDate, endDate);
        return pdf("Laporan_QC_Defect.pdf", reportService.generateNgDataPdf(startDate, endDate, includePhotos));
    }

    @GetMapping("/download-ng-data-excel")
    public ResponseEntity<byte[]> downloadNgDataReportExcel(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "true") boolean includePhotos) {
        checkDateRange(startDate, endDate);
        return excel("Laporan_QC_Defect.xlsx", reportService.generateNgDataExcel(startDate, endDate, includePhotos));
    }

    @GetMapping("/download-production-csv")
    public ResponseEntity<String> downloadProductionCsv(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        checkDateRange(startDate, endDate);
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=Laporan_Produksi.csv");
        headers.add(HttpHeaders.CONTENT_TYPE, "text/csv; charset=UTF-8");
        return new ResponseEntity<>(reportService.generateProductionCsv(startDate, endDate), headers, HttpStatus.OK);
    }

    @GetMapping("/download-production-pdf")
    public ResponseEntity<byte[]> downloadProductionPdf(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        checkDateRange(startDate, endDate);
        return pdf("Laporan_Produksi.pdf", reportService.generateProductionPdf(startDate, endDate));
    }

    @GetMapping("/download-production-excel")
    public ResponseEntity<byte[]> downloadProductionExcel(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        checkDateRange(startDate, endDate);
        return excel("Laporan_Produksi.xlsx", reportService.generateProductionExcel(startDate, endDate));
    }

    private ResponseEntity<byte[]> pdf(String filename, byte[] bytes) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename);
        return ResponseEntity.ok().headers(headers).contentType(MediaType.APPLICATION_PDF).body(bytes);
    }

    private ResponseEntity<byte[]> excel(String filename, byte[] bytes) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename);
        return ResponseEntity.ok().headers(headers)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(bytes);
    }
}
