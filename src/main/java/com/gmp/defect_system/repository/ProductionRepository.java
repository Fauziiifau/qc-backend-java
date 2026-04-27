package com.gmp.defect_system.repository;

import com.gmp.defect_system.entity.Production;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.time.LocalDate;

@Repository
public interface ProductionRepository extends JpaRepository<Production, Long> {

    @Query("SELECT p.productionDate AS productionDate, " +
            "p.shift AS shift, " +
            "SUM(p.targetQuantity) AS targetQuantity, " +
            "SUM(p.producedQuantity) AS producedQuantity " +
            "FROM Production p " +
            "WHERE p.productionDate BETWEEN :startDate AND :endDate " +
            "GROUP BY p.productionDate, p.shift " +
            "ORDER BY p.productionDate ASC")
    List<DailyProductionProjection> getDailyProductionAggregation(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );
}