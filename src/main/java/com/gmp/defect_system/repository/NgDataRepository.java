package com.gmp.defect_system.repository;

import com.gmp.defect_system.dto.ParetoDto;
import com.gmp.defect_system.entity.NgData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface NgDataRepository extends JpaRepository<NgData, Long> {

    List<NgData> findAllByOrderByIdAsc();

    @Query("SELECT n.defectType.name as category, SUM(n.quantity) as totalCount " +
            "FROM NgData n " +
            "WHERE n.defectDate BETWEEN :startDate AND :endDate " +
            "GROUP BY n.defectType.name " +
            "ORDER BY totalCount DESC")
    List<Object[]> getTotalDefectsByType(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    @Query("SELECT new com.gmp.defect_system.dto.ParetoDto(d.name, p.partName, SUM(n.quantity)) " +
            "FROM NgData n " +
            "JOIN n.defectType d " +
            "JOIN n.part p " +
            "WHERE n.defectDate BETWEEN :startDate AND :endDate " +
            "GROUP BY d.name, p.partName " +
            "ORDER BY SUM(n.quantity) DESC")
    List<ParetoDto> getParetoChartData(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    @Query("SELECT n.proses FROM NgData n WHERE n.defectType.name = :defectName ORDER BY n.id DESC")
    List<String> findTopProsesByDefectName(@Param("defectName") String defectName);
}