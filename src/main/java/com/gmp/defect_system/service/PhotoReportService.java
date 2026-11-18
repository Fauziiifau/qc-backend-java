package com.gmp.defect_system.service;

import com.gmp.defect_system.entity.CustomerComplaint;
import com.gmp.defect_system.entity.NgData;
import com.gmp.defect_system.entity.Production;
import com.gmp.defect_system.repository.ExportCustomerComplaintRepository;
import com.gmp.defect_system.repository.ExportNgDataRepository;
import com.gmp.defect_system.repository.ExportProductionRepository;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.ClientAnchor;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Drawing;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class PhotoReportService {
    private static final int MAX_DISTINCT_PHOTOS_PER_EXPORT = 60;
    private final ExportNgDataRepository ngDataRepository;
    private final ExportProductionRepository productionRepository;
    private final ExportCustomerComplaintRepository complaintRepository;
    private final DefectPhotoStorageService photoStorage;

    public PhotoReportService(ExportNgDataRepository ngDataRepository,
                         ExportProductionRepository productionRepository,
                         ExportCustomerComplaintRepository complaintRepository,
                         DefectPhotoStorageService photoStorage) {
        this.ngDataRepository = ngDataRepository;
        this.productionRepository = productionRepository;
        this.complaintRepository = complaintRepository;
        this.photoStorage = photoStorage;
    }

    private List<NgData> defects(LocalDate start, LocalDate end) {
        return start == null && end == null
                ? ngDataRepository.findAllByOrderByIdAsc()
                : ngDataRepository.findByDefectDateBetweenOrderByIdAsc(
                        start == null ? LocalDate.of(1900, 1, 1) : start,
                        end == null ? LocalDate.of(9999, 12, 31) : end);
    }

    private List<CustomerComplaint> complaints(LocalDate start, LocalDate end) {
        return start == null && end == null
                ? complaintRepository.findAll()
                : complaintRepository.findByComplaintDateBetweenOrderByIdAsc(
                        start == null ? LocalDate.of(1900, 1, 1) : start,
                        end == null ? LocalDate.of(9999, 12, 31) : end);
    }

    private List<Production> productions(LocalDate start, LocalDate end) {
        return start == null && end == null
                ? productionRepository.findAll()
                : productionRepository.findByProductionDateBetweenOrderByIdAsc(
                        start == null ? LocalDate.of(1900, 1, 1) : start,
                        end == null ? LocalDate.of(9999, 12, 31) : end);
    }

    /** Cache lokal satu request supaya satu foto tidak diunduh ulang untuk beberapa defect. */
    private Optional<byte[]> photo(Map<String, Optional<byte[]>> cache, String objectName) {
        if (objectName == null || objectName.isBlank()) return Optional.empty();
        String key = objectName.trim();
        if (!cache.containsKey(key) && cache.size() >= MAX_DISTINCT_PHOTOS_PER_EXPORT) {
            return Optional.empty();
        }
        return cache.computeIfAbsent(key, photoStorage::fetchImage);
    }

    private String photoPath(String name, boolean includePhotos) {
        if (!includePhotos || name == null || name.isBlank()) return "";
        return photoStorage.storageReference(name);
    }

    // Escape field CSV, termasuk tanda kutip, koma, baris baru, dan formula Excel.
    private static String csvField(Object value) {
        String text = value == null ? "" : String.valueOf(value);
        String trimmed = text.stripLeading();
        if (!trimmed.isEmpty() && "=+-@".indexOf(trimmed.charAt(0)) >= 0) {
            text = "'" + text;
        }
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }

    private static void csvRow(StringBuilder csv, Object... values) {
        for (int i = 0; i < values.length; i++) {
            if (i > 0) csv.append(',');
            csv.append(csvField(values[i]));
        }
        csv.append("\r\n");
    }

    public String generateNgDataCsv(LocalDate start, LocalDate end, boolean includePhotos) {
        StringBuilder csv = new StringBuilder("\uFEFF");
        csv.append("--- DATA DEFECT INTERNAL ---\r\n");
        csvRow(csv, "ID", "Tanggal", "Part Number", "Nama Part", "Jenis Cacat", "Quantity",
                "Operator", "Keterangan", "Bukti Foto (path)");
        for (NgData data : defects(start, end)) {
            csvRow(csv, data.getId(), data.getDefectDate(), data.getPart().getPartNumber(),
                    data.getPart().getPartName(), data.getDefectType().getName(), data.getQuantity(),
                    data.getOperatorName(), data.getRemarks(), photoPath(data.getPhotoUrl(), includePhotos));
        }

        csv.append("\r\n--- DATA KOMPLAIN CUSTOMER ---\r\n");
        csvRow(csv, "ID", "Tanggal", "Customer", "Nama Part", "Jenis Cacat", "Quantity",
                "Lot Number", "Deskripsi Masalah", "Bukti Foto (path)");
        for (CustomerComplaint data : complaints(start, end)) {
            csvRow(csv, data.getId(), data.getComplaintDate(), data.getCustomerName(),
                    data.getPart().getPartName(), data.getDefectType().getName(), data.getDefectQuantity(),
                    data.getLotNumber(), data.getProblemDescription(), photoPath(data.getPhotoUrl(), includePhotos));
        }
        return csv.toString();
    }

    private static void pdfHeaders(PdfPTable table, String[] headers, Font font, Color background) {
        for (String header : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(header, font));
            cell.setBackgroundColor(background);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell.setPadding(5);
            table.addCell(cell);
        }
    }

    private static void pdfCell(PdfPTable table, Object value, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(value == null ? "-" : String.valueOf(value), font));
        cell.setPadding(4);
        table.addCell(cell);
    }

    private static void addPhotoReference(Map<String, List<String>> grouped, String file, String label) {
        if (file != null && !file.isBlank()) {
            grouped.computeIfAbsent(file.trim(), unused -> new ArrayList<>()).add(label);
        }
    }

    private void pdfPhotoAppendix(Document document, String section,
                                Map<String, List<String>> grouped,
                                Map<String, Optional<byte[]>> cache) throws Exception {
        if (grouped.isEmpty()) return;
        document.newPage();
        document.add(new Paragraph(section, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14)));
        document.add(new Paragraph("Foto ditampilkan sesuai ID laporan. Satu foto dapat dipakai beberapa ID."));
        document.add(Chunk.NEWLINE);

        Font small = FontFactory.getFont(FontFactory.HELVETICA, 9);
        for (Map.Entry<String, List<String>> entry : grouped.entrySet()) {
            PdfPTable item = new PdfPTable(1);
            item.setWidthPercentage(100);
            item.setSpacingAfter(12);
            PdfPCell label = new PdfPCell(new Phrase(String.join(", ", entry.getValue()), small));
            label.setBackgroundColor(Color.LIGHT_GRAY);
            label.setPadding(6);
            item.addCell(label);

            Optional<byte[]> image = photo(cache, entry.getKey());
            if (image.isPresent()) {
                try {
                    Image img = Image.getInstance(image.get());
                    img.scaleToFit(380, 240);
                    PdfPCell pictureCell = new PdfPCell(img, true);
                    pictureCell.setPadding(8);
                    pictureCell.setHorizontalAlignment(Element.ALIGN_CENTER);
                    item.addCell(pictureCell);
                } catch (Exception e) {
                    pdfCell(item, "Gambar tidak dapat dibaca: " + entry.getKey(), small);
                }
            } else {
                pdfCell(item, "Foto tidak tersedia: " + entry.getKey(), small);
            }
            document.add(item);
        }
    }

    public byte[] generateNgDataPdf(LocalDate start, LocalDate end, boolean includePhotos) {
        try {
            List<NgData> semuaNg = defects(start, end);
            List<CustomerComplaint> semuaKomplain = complaints(start, end);
            Map<String, Optional<byte[]>> cache = new HashMap<>();
            Map<String, List<String>> ngImages = new LinkedHashMap<>();
            Map<String, List<String>> complaintImages = new LinkedHashMap<>();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Document document = new Document(PageSize.A4);
            PdfWriter.getInstance(document, out);
            document.open();

            Font title = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
            Font fontHeader = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9);
            Font fontData = FontFactory.getFont(FontFactory.HELVETICA, 9);
            Paragraph judul1 = new Paragraph("Laporan QC Defect Monitoring (Internal)", title);
            judul1.setAlignment(Element.ALIGN_CENTER);
            document.add(judul1);
            document.add(Chunk.NEWLINE);

            PdfPTable tableNg = new PdfPTable(7);
            tableNg.setWidthPercentage(100);
            tableNg.setWidths(new float[]{1f, 2f, 3f, 3f, 1.5f, 2f, 3f});
            pdfHeaders(tableNg, new String[]{"ID", "Tanggal", "Nama Part", "Jenis Cacat", "Qty", "Operator", "Keterangan"},
                    fontHeader, Color.LIGHT_GRAY);
            for (NgData data : semuaNg) {
                pdfCell(tableNg, data.getId(), fontData);
                pdfCell(tableNg, data.getDefectDate(), fontData);
                pdfCell(tableNg, data.getPart().getPartName(), fontData);
                pdfCell(tableNg, data.getDefectType().getName(), fontData);
                pdfCell(tableNg, data.getQuantity(), fontData);
                pdfCell(tableNg, data.getOperatorName(), fontData);
                pdfCell(tableNg, data.getRemarks(), fontData);
                if (includePhotos) addPhotoReference(ngImages, data.getPhotoUrl(), "Defect ID " + data.getId());
            }
            document.add(tableNg);
            document.add(Chunk.NEWLINE);
            document.add(Chunk.NEWLINE);

            Paragraph judul2 = new Paragraph("Laporan Customer Complaint (Eksternal)", title);
            judul2.setAlignment(Element.ALIGN_CENTER);
            document.add(judul2);
            document.add(Chunk.NEWLINE);
            PdfPTable tableKomplain = new PdfPTable(7);
            tableKomplain.setWidthPercentage(100);
            tableKomplain.setWidths(new float[]{1f, 2f, 3f, 3f, 2.5f, 1.5f, 3f});
            pdfHeaders(tableKomplain, new String[]{"ID", "Tanggal", "Customer", "Nama Part", "Jenis Cacat", "Qty", "Deskripsi"},
                    fontHeader, new Color(255, 204, 204));
            for (CustomerComplaint data : semuaKomplain) {
                pdfCell(tableKomplain, data.getId(), fontData);
                pdfCell(tableKomplain, data.getComplaintDate(), fontData);
                pdfCell(tableKomplain, data.getCustomerName(), fontData);
                pdfCell(tableKomplain, data.getPart().getPartName(), fontData);
                pdfCell(tableKomplain, data.getDefectType().getName(), fontData);
                pdfCell(tableKomplain, data.getDefectQuantity(), fontData);
                pdfCell(tableKomplain, data.getProblemDescription(), fontData);
                if (includePhotos) addPhotoReference(complaintImages, data.getPhotoUrl(), "Komplain ID " + data.getId());
            }
            document.add(tableKomplain);

            if (includePhotos) {
                pdfPhotoAppendix(document, "Lampiran Foto Defect Internal", ngImages, cache);
                pdfPhotoAppendix(document, "Lampiran Foto Customer Complaint", complaintImages, cache);
            }
            document.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Gagal membuat PDF laporan defect: " + e.getMessage(), e);
        }
    }

    private static CellStyle headerStyle(Workbook workbook, IndexedColors color) {
        CellStyle style = workbook.createCellStyle();
        org.apache.poi.ss.usermodel.Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(color.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    private static void sheetHeader(Sheet sheet, String[] headers, CellStyle style) {
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(style);
        }
    }

    private void excelPhoto(Workbook workbook, Sheet sheet, Drawing<?> drawing,
                            Map<String, Integer> pictureIndices,
                            Map<String, Optional<byte[]>> cache,
                            Row row, int rowIndex, int imageColumn, String name) {
        if (name == null || name.isBlank()) return;
        Optional<byte[]> bytes = photo(cache, name);
        if (bytes.isEmpty()) {
            row.createCell(imageColumn).setCellValue("Foto tidak tersedia");
            return;
        }
        int index = pictureIndices.computeIfAbsent(name.trim(), key ->
                workbook.addPicture(bytes.get(), DefectPhotoStorageService.poiPictureType(bytes.get())));
        row.setHeightInPoints(90);
        CreationHelper helper = workbook.getCreationHelper();
        ClientAnchor anchor = helper.createClientAnchor();
        anchor.setCol1(imageColumn);
        anchor.setRow1(rowIndex);
        anchor.setCol2(imageColumn + 1);
        anchor.setRow2(rowIndex + 1);
        drawing.createPicture(anchor, index);
    }

    public byte[] generateNgDataExcel(LocalDate start, LocalDate end, boolean includePhotos) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Map<String, Optional<byte[]>> cache = new HashMap<>();
            Map<String, Integer> pictureIndices = new HashMap<>();
            List<NgData> semuaNg = defects(start, end);
            Sheet sheet1 = workbook.createSheet("Defect Internal");
            CellStyle style1 = headerStyle(workbook, IndexedColors.GREY_25_PERCENT);
            String[] headers1 = {"ID", "Tanggal", "Part Number", "Nama Part", "Jenis Cacat", "Quantity",
                    "Operator", "Keterangan", "Bukti Foto (path)", "Bukti Foto"};
            sheetHeader(sheet1, headers1, style1);
            Drawing<?> drawing1 = sheet1.createDrawingPatriarch();
            int rowIdx1 = 1;
            for (NgData data : semuaNg) {
                int idx = rowIdx1++;
                Row row = sheet1.createRow(idx);
                row.createCell(0).setCellValue(data.getId());
                row.createCell(1).setCellValue(String.valueOf(data.getDefectDate()));
                row.createCell(2).setCellValue(data.getPart().getPartNumber());
                row.createCell(3).setCellValue(data.getPart().getPartName());
                row.createCell(4).setCellValue(data.getDefectType().getName());
                row.createCell(5).setCellValue(data.getQuantity());
                row.createCell(6).setCellValue(data.getOperatorName() == null ? "" : data.getOperatorName());
                row.createCell(7).setCellValue(data.getRemarks() == null ? "" : data.getRemarks());
                row.createCell(8).setCellValue(photoPath(data.getPhotoUrl(), includePhotos));
                if (includePhotos) excelPhoto(workbook, sheet1, drawing1, pictureIndices, cache,
                        row, idx, 9, data.getPhotoUrl());
            }
            for (int i = 0; i < 8; i++) sheet1.autoSizeColumn(i);
            sheet1.setColumnWidth(8, 40 * 256);
            sheet1.setColumnWidth(9, 25 * 256);

            List<CustomerComplaint> semuaKomplain = complaints(start, end);
            Sheet sheet2 = workbook.createSheet("Komplain Customer");
            CellStyle style2 = headerStyle(workbook, IndexedColors.ROSE);
            String[] headers2 = {"ID", "Tanggal", "Customer", "Part Number", "Nama Part", "Jenis Cacat",
                    "Quantity", "Lot Number", "Deskripsi Masalah", "Bukti Foto (path)", "Bukti Foto"};
            sheetHeader(sheet2, headers2, style2);
            Drawing<?> drawing2 = sheet2.createDrawingPatriarch();
            int rowIdx2 = 1;
            for (CustomerComplaint data : semuaKomplain) {
                int idx = rowIdx2++;
                Row row = sheet2.createRow(idx);
                row.createCell(0).setCellValue(data.getId());
                row.createCell(1).setCellValue(String.valueOf(data.getComplaintDate()));
                row.createCell(2).setCellValue(data.getCustomerName() == null ? "" : data.getCustomerName());
                row.createCell(3).setCellValue(data.getPart().getPartNumber());
                row.createCell(4).setCellValue(data.getPart().getPartName());
                row.createCell(5).setCellValue(data.getDefectType().getName());
                row.createCell(6).setCellValue(data.getDefectQuantity());
                row.createCell(7).setCellValue(data.getLotNumber() == null ? "" : data.getLotNumber());
                row.createCell(8).setCellValue(data.getProblemDescription() == null ? "" : data.getProblemDescription());
                row.createCell(9).setCellValue(photoPath(data.getPhotoUrl(), includePhotos));
                if (includePhotos) excelPhoto(workbook, sheet2, drawing2, pictureIndices, cache,
                        row, idx, 10, data.getPhotoUrl());
            }
            for (int i = 0; i < 9; i++) sheet2.autoSizeColumn(i);
            sheet2.setColumnWidth(9, 40 * 256);
            sheet2.setColumnWidth(10, 25 * 256);

            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Gagal membuat Excel laporan defect: " + e.getMessage(), e);
        }
    }

    // Data produksi TIDAK memiliki photo_url. Export produksi tidak disisipkan foto defect sembarang.
    public String generateProductionCsv(LocalDate start, LocalDate end) {
        StringBuilder csv = new StringBuilder("\uFEFF");
        csvRow(csv, "ID", "Tanggal", "Part Number", "Nama Part", "Target Qty", "Produced Qty", "Status");
        for (Production data : productions(start, end)) {
            String status = data.getProducedQuantity() >= data.getTargetQuantity() ? "On Target" : "Below Target";
            csvRow(csv, data.getId(), data.getProductionDate(), data.getPart().getPartNumber(),
                    data.getPart().getPartName(), data.getTargetQuantity(), data.getProducedQuantity(), status);
        }
        return csv.toString();
    }

    public byte[] generateProductionPdf(LocalDate start, LocalDate end) {
        try {
            List<Production> semuaData = productions(start, end);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Document document = new Document(PageSize.A4);
            PdfWriter.getInstance(document, out);
            document.open();
            Paragraph judul = new Paragraph("Laporan Harian Hasil Produksi",
                    FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18));
            judul.setAlignment(Element.ALIGN_CENTER);
            document.add(judul);
            document.add(Chunk.NEWLINE);
            PdfPTable table = new PdfPTable(7);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{1f, 2f, 2.5f, 3f, 1.5f, 1.5f, 2f});
            Font fontHeader = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
            Font fontData = FontFactory.getFont(FontFactory.HELVETICA, 10);
            pdfHeaders(table, new String[]{"ID", "Tanggal", "Part Number", "Nama Part", "Target", "Aktual", "Status"},
                    fontHeader, Color.LIGHT_GRAY);
            for (Production data : semuaData) {
                String status = data.getProducedQuantity() >= data.getTargetQuantity() ? "On Target" : "Below Target";
                pdfCell(table, data.getId(), fontData);
                pdfCell(table, data.getProductionDate(), fontData);
                pdfCell(table, data.getPart().getPartNumber(), fontData);
                pdfCell(table, data.getPart().getPartName(), fontData);
                pdfCell(table, data.getTargetQuantity(), fontData);
                pdfCell(table, data.getProducedQuantity(), fontData);
                pdfCell(table, status, fontData);
            }
            document.add(table);
            document.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Gagal membuat PDF laporan produksi: " + e.getMessage(), e);
        }
    }

    public byte[] generateProductionExcel(LocalDate start, LocalDate end) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            List<Production> semuaData = productions(start, end);
            Sheet sheet = workbook.createSheet("Data Produksi");
            sheetHeader(sheet, new String[]{"ID", "Tanggal", "Part Number", "Nama Part", "Target Qty", "Produced Qty", "Status"},
                    headerStyle(workbook, IndexedColors.GREY_25_PERCENT));
            int rowIdx = 1;
            for (Production data : semuaData) {
                String status = data.getProducedQuantity() >= data.getTargetQuantity() ? "On Target" : "Below Target";
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(data.getId());
                row.createCell(1).setCellValue(String.valueOf(data.getProductionDate()));
                row.createCell(2).setCellValue(data.getPart().getPartNumber());
                row.createCell(3).setCellValue(data.getPart().getPartName());
                row.createCell(4).setCellValue(data.getTargetQuantity());
                row.createCell(5).setCellValue(data.getProducedQuantity());
                row.createCell(6).setCellValue(status);
            }
            for (int i = 0; i < 7; i++) sheet.autoSizeColumn(i);
            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Gagal membuat Excel laporan produksi: " + e.getMessage(), e);
        }
    }
}
