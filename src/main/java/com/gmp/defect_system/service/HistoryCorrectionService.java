package com.gmp.defect_system.service;

import com.gmp.defect_system.controller.HistoryController.DefectCorrection;
import com.gmp.defect_system.controller.HistoryController.ProductionCorrection;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@Service
public class HistoryCorrectionService {
    private final JdbcTemplate jdbc;
    public HistoryCorrectionService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Map<String,Object>> history(String type, LocalDate start, LocalDate end) {
        validatePeriod(start, end);
        if ("production".equalsIgnoreCase(type)) {
            return jdbc.queryForList("""
                SELECT p.id, p.production_date AS "productionDate", p.shift,
                       p.part_id AS "partId", pt.part_name AS "partName", pt.part_number AS "partNumber",
                       p.process_name AS "processName", p.machine_name AS "machineName",
                       p.production_line AS "productionLine", p.created_by AS "operatorName",
                       p.target_quantity AS "targetQuantity", p.produced_quantity AS "producedQuantity",
                       CASE WHEN EXISTS (SELECT 1 FROM public.qc_correction_audit a
                         WHERE a.entity_type='PRODUCTION' AND a.entity_id=p.id)
                         THEN 'Dikoreksi' ELSE 'Tercatat' END AS status
                FROM public.production_data p JOIN public.parts pt ON pt.id=p.part_id
                WHERE p.production_date BETWEEN ? AND ?
                ORDER BY p.production_date DESC, p.id DESC LIMIT 500
                """, start, end);
        }
        if ("defect".equalsIgnoreCase(type)) {
            return jdbc.queryForList("""
                SELECT n.id, n.defect_date AS "defectDate", n.shift,
                       n.part_id AS "partId", pt.part_name AS "partName", pt.part_number AS "partNumber",
                       n.defect_type_id AS "defectTypeId", dt.name AS "defectName",
                       n.proses, n.nama_mesin AS "machineName", n.operator_name AS "operatorName",
                       n.quantity, n.remarks,
                       CASE WHEN EXISTS (SELECT 1 FROM public.qc_correction_audit a
                         WHERE a.entity_type='DEFECT' AND a.entity_id=n.id)
                         THEN 'Dikoreksi' ELSE 'Tercatat' END AS status
                FROM public.ng_data n
                JOIN public.parts pt ON pt.id=n.part_id
                JOIN public.defect_types dt ON dt.id=n.defect_type_id
                WHERE n.defect_date BETWEEN ? AND ?
                ORDER BY n.defect_date DESC, n.id DESC LIMIT 500
                """, start, end);
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Jenis riwayat tidak dikenal");
    }

    public List<Map<String,Object>> audit(String type, Long id) {
        String entityType = switch (type.toLowerCase()) {
            case "production" -> "PRODUCTION";
            case "defect" -> "DEFECT";
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Jenis tidak dikenal");
        };
        return jdbc.queryForList("""
            SELECT id, corrected_at AS "correctedAt", corrected_by AS "correctedBy",
                   reason, old_data::text AS "oldData", new_data::text AS "newData"
            FROM public.qc_correction_audit WHERE entity_type=? AND entity_id=?
            ORDER BY corrected_at DESC LIMIT 100
            """, entityType, id);
    }

    @Transactional
    public void updateProduction(Long id, ProductionCorrection d, String actor) {
        validatePart(d.partId());
        String before = snapshot("production_data", id);
        jdbc.update("""
            UPDATE public.production_data SET production_date=?, shift=?, part_id=?,
                process_name=?, machine_name=?, target_quantity=?, produced_quantity=? WHERE id=?
            """, d.productionDate(), d.shift().trim(), d.partId(), trim(d.processName()),
                trim(d.machineName()), d.targetQuantity(), d.producedQuantity(), id);
        insertAudit("PRODUCTION", id, before, snapshot("production_data",id), d.reason(), actor);
    }

    @Transactional
    public void updateDefect(Long id, DefectCorrection d, String actor) {
        validatePart(d.partId());
        checkExists("defect_types", d.defectTypeId());
        String before = snapshot("ng_data", id);
        jdbc.update("""
            UPDATE public.ng_data SET defect_date=?, shift=?, part_id=?, defect_type_id=?,
                proses=?, operator_name=?, quantity=?, remarks=? WHERE id=?
            """, d.defectDate(), trim(d.shift()), d.partId(), d.defectTypeId(),
                trim(d.proses()), trim(d.operatorName()), d.quantity(), trim(d.remarks()), id);
        // photo_url dan created_by sengaja tidak diubah.
        insertAudit("DEFECT", id, before, snapshot("ng_data",id), d.reason(), actor);
    }

    private void validatePeriod(LocalDate start, LocalDate end) {
        if (start == null || end == null || start.isAfter(end) ||
                ChronoUnit.DAYS.between(start,end) > 366)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Tanggal tidak valid atau rentang melebihi 366 hari");
    }

    // Nama tabel selalu ditentukan kode, tidak pernah berasal dari input pengguna.
    private String snapshot(String table, Long id) {
        // Nama tabel hanya berasal dari pilihan statis di kode (bukan parameter user).
        if (!"production_data".equals(table) && !"ng_data".equals(table))
            throw new IllegalArgumentException("Unknown table");
        try {
            // PostgreSQL membuat JSON untuk snapshot, sehingga tidak butuh Jackson ObjectMapper.
            // FOR UPDATE menjaga konsistensi jika ada dua permintaan koreksi bersamaan.
            return jdbc.queryForObject(
                    "SELECT to_jsonb(t)::text FROM public." + table + " t WHERE t.id=? FOR UPDATE",
                    String.class, id);
        } catch (EmptyResultDataAccessException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaksi tidak ditemukan");
        }
    }

    private void validatePart(Long id) { checkExists("parts", id); }
    private void checkExists(String table, Long id) {
        if (!"parts".equals(table) && !"defect_types".equals(table)) throw new IllegalArgumentException();
        Long count = jdbc.queryForObject("SELECT count(*) FROM public." + table + " WHERE id=?", Long.class, id);
        if (count == null || count == 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Master data tidak ditemukan");
    }

    private void insertAudit(String type, Long id, String before,
                             String after, String reason, String actor) {
        jdbc.update("""
            INSERT INTO public.qc_correction_audit
            (entity_type, entity_id, old_data, new_data, reason, corrected_by)
            VALUES (?, ?, CAST(? AS jsonb), CAST(? AS jsonb), ?, ?)
            """, type, id, before, after, reason.trim(), actor);
    }
    private String trim(String v) { return v == null ? null : v.trim(); }
}