package com.gmp.defect_system.repository;

import com.gmp.defect_system.entity.NgData;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;

/** Repository khusus export v2: tidak mengubah query dashboard/NG yang sudah ada. */
public interface ExportNgDataRepository extends JpaRepository<NgData, Long> {
    List<NgData> findAllByOrderByIdAsc();
    List<NgData> findByDefectDateBetweenOrderByIdAsc(LocalDate startDate, LocalDate endDate);
}
