package com.gmp.defect_system.dto;

/** Hasil agregasi jenis defect per item, untuk membuat Pareto per item. */
public record ItemDefectRow(
        Long itemId,
        String itemName,
        String partNumber,
        String defectName,
        Long quantity
) {}