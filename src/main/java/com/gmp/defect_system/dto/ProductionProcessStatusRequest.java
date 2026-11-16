package com.gmp.defect_system.dto;

import jakarta.validation.constraints.NotNull;

public record ProductionProcessStatusRequest(
        @NotNull(message = "Status aktif wajib diisi")
        Boolean isActive
) {}
