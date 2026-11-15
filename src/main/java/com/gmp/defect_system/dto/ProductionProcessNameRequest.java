package com.gmp.defect_system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProductionProcessNameRequest(
        @NotBlank(message = "Nama proses wajib diisi")
        @Size(max = 150, message = "Nama proses maksimal 150 karakter")
        String processName
) {}
