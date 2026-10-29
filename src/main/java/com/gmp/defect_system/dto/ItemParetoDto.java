package com.gmp.defect_system.dto;

import java.util.List;

/** Satu item/part dengan total NG, jumlah jenis defect, dan rinciannya. */
public record ItemParetoDto(
        Long itemId,
        String itemName,
        String partNumber,
        long totalQuantity,
        int totalDefectTypes,
        double cumulativePercentage,
        List<DefectDetailDto> defects
) {
    public record DefectDetailDto(String defectName, long quantity) {}
}
