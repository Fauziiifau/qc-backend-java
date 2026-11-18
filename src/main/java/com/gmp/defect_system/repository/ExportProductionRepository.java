package com.gmp.defect_system.repository;

import com.gmp.defect_system.entity.Production;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;

/** Repository khusus export v2. */
public interface ExportProductionRepository extends JpaRepository<Production, Long> {
    List<Production> findByProductionDateBetweenOrderByIdAsc(LocalDate startDate, LocalDate endDate);
}
