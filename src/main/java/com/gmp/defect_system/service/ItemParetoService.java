package com.gmp.defect_system.service;

import com.gmp.defect_system.dto.ItemDefectRow;
import com.gmp.defect_system.dto.ItemParetoDto;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Layanan agregasi NG berdasarkan ID item, tanpa mengubah DashboardService lama. */
@Service
public class ItemParetoService {
    private static final String JPQL =
            "SELECT new com.gmp.defect_system.dto.ItemDefectRow(" +
                    "p.id, p.partName, p.partNumber, d.name, SUM(n.quantity)) " +
                    "FROM NgData n " +
                    "JOIN n.part p " +
                    "JOIN n.defectType d " +
                    "WHERE n.defectDate BETWEEN :startDate AND :endDate " +
                    "GROUP BY p.id, p.partName, p.partNumber, d.name";

    private final EntityManager entityManager;

    public ItemParetoService(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    /**
     * Pareto per item: total NG setiap item dihitung dari seluruh jenis defect.
     * ID item digunakan sebagai key untuk menghindari penggabungan dua part
     * yang memiliki nama sama. Tidak mengubah endpoint Pareto lama.
     */
    public List<ItemParetoDto> getParetoByItem(LocalDate start, LocalDate end) {
        List<ItemDefectRow> rows = entityManager.createQuery(JPQL, ItemDefectRow.class)
                .setParameter("startDate", start)
                .setParameter("endDate", end)
                .getResultList();
        Map<Long, List<ItemDefectRow>> byItem = new LinkedHashMap<>();
        for (ItemDefectRow row : rows) {
            byItem.computeIfAbsent(row.itemId(), id -> new ArrayList<>()).add(row);
        }

        List<ItemParetoDto> items = new ArrayList<>();
        for (List<ItemDefectRow> group : byItem.values()) {
            ItemDefectRow first = group.get(0);
            long total = 0;
            List<ItemParetoDto.DefectDetailDto> details = new ArrayList<>();
            for (ItemDefectRow row : group) {
                long qty = row.quantity() == null ? 0L : row.quantity();
                total += qty;
                details.add(new ItemParetoDto.DefectDetailDto(row.defectName(), qty));
            }
            details.sort(Comparator.comparingLong(ItemParetoDto.DefectDetailDto::quantity).reversed());
            items.add(new ItemParetoDto(first.itemId(), first.itemName(), first.partNumber(),
                    total, details.size(), 0.0, details));
        }

        // Pareto: nilai terbesar lebih dahulu, bukan urutan alfabet.
        items.sort(Comparator.comparingLong(ItemParetoDto::totalQuantity).reversed()
                .thenComparing(ItemParetoDto::itemName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(ItemParetoDto::itemId));
        long grandTotal = items.stream().mapToLong(ItemParetoDto::totalQuantity).sum();
        long runningTotal = 0;
        List<ItemParetoDto> result = new ArrayList<>();
        for (ItemParetoDto item : items) {
            runningTotal += item.totalQuantity();
            double cumulative = grandTotal == 0 ? 0.0 :
                    Math.round((runningTotal * 10000.0) / grandTotal) / 100.0;
            result.add(new ItemParetoDto(item.itemId(), item.itemName(), item.partNumber(),
                    item.totalQuantity(), item.totalDefectTypes(), cumulative, item.defects()));
        }
        return result;
    }

}