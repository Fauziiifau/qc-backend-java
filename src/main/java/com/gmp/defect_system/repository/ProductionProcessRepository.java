package com.gmp.defect_system.repository;

import com.gmp.defect_system.entity.ProductionProcess;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductionProcessRepository extends JpaRepository<ProductionProcess, Long> {
    List<ProductionProcess> findAllByOrderByProcessNameAsc();
    List<ProductionProcess> findByIsActiveTrueOrderByProcessNameAsc();
    Optional<ProductionProcess> findByProcessNameIgnoreCase(String processName);

    // Jangan ganti nama proses yang sudah dipakai transaksi/konfigurasi lama.
    @Query(value = """
            SELECT EXISTS (
                SELECT 1 FROM public.production_data WHERE LOWER(process_name) = LOWER(:name)
            ) OR EXISTS (
                SELECT 1 FROM public.ng_data WHERE LOWER(proses) = LOWER(:name)
            ) OR EXISTS (
                SELECT 1 FROM public.defect_types WHERE LOWER(proses) = LOWER(:name)
            )
            """, nativeQuery = true)
    boolean isNameReferenced(@Param("name") String name);
}
