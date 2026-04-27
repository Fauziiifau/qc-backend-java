package com.gmp.defect_system.service;

import com.gmp.defect_system.dto.DailyProductionDto;
import com.gmp.defect_system.repository.DailyProductionProjection;
import com.gmp.defect_system.dto.ParetoDto;
import com.gmp.defect_system.dto.KpiDto;
import com.gmp.defect_system.repository.ProductionRepository;
import com.gmp.defect_system.repository.NgDataRepository;
import com.gmp.defect_system.repository.CustomerComplaintRepository;

import org.springframework.stereotype.Service;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.ArrayList;
import java.util.List;
import java.time.LocalDate;
import java.util.Map;

@Service
public class DashboardService {
    private final NgDataRepository ngDataRepository;
    private final ProductionRepository productionRepository;
    private final CustomerComplaintRepository complaintRepository;

    public DashboardService(NgDataRepository ngDataRepository, ProductionRepository productionRepository, CustomerComplaintRepository complaintRepository) {
        this.ngDataRepository = ngDataRepository;
        this.productionRepository = productionRepository;
        this.complaintRepository = complaintRepository;
    }
    public List<ParetoDto> getParetoChartData(LocalDate start, LocalDate end) {
        return ngDataRepository.getParetoChartData(start, end);
    }
    public KpiDto getKpiSummary(LocalDate start, LocalDate end) {
        List<Object[]> paretoData = ngDataRepository.getTotalDefectsByType(start, end);
        long totalNg = 0;
        for (Object[] row : paretoData) {
            totalNg += (Long) row[1];
        }

        long totalProduction = 0;
        List<com.gmp.defect_system.entity.Production> allProduction = productionRepository.findAll();
        for (com.gmp.defect_system.entity.Production prod : allProduction) {
            totalProduction += prod.getProducedQuantity();
        }

        double defectRate = 0.0;
        if (totalProduction > 0) {
            defectRate = Math.round(((double) totalNg / totalProduction * 100) * 100.0) / 100.0;
        }

        return new KpiDto(totalProduction, totalNg, defectRate);
    }

    public List<DailyProductionDto> getDailyProductionData(LocalDate start, LocalDate end) {
        List<DailyProductionProjection> aggregations = productionRepository.getDailyProductionAggregation(start, end);
        List<DailyProductionDto> result = new ArrayList<>();
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH);
        DateTimeFormatter dayFormatter = DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH);
        for (DailyProductionProjection agg : aggregations) {
            DailyProductionDto dto = new DailyProductionDto();
            dto.setDate(agg.getProductionDate().format(dateFormatter));
            dto.setDay(agg.getProductionDate().format(dayFormatter));
            String shiftData = agg.getShift();
            dto.setShift(shiftData != null && !shiftData.isEmpty() ? shiftData : "-");
            Long target = agg.getTargetQuantity() != null ? agg.getTargetQuantity() : 0L;
            Long produced = agg.getProducedQuantity() != null ? agg.getProducedQuantity() : 0L;
            dto.setTarget(target);
            dto.setProduced(produced);
            dto.setVariance(produced - target);
            int efficiency = 0;
            if (target > 0) {
                efficiency = (int) Math.round((produced.doubleValue() / target.doubleValue()) * 100);
            }
            dto.setEfficiency(efficiency);
            if (efficiency >= 100) {
                dto.setStatus("On Target");
            } else {
                dto.setStatus("Below Target");
            }
            result.add(dto);
        }
        return result;
    }
    public long getComplaintCount(LocalDate start, LocalDate end) {
        return complaintRepository.getComplaintCountByDate(start, end);
    }
    public List<Map<String, Object>> getComplaintParetoData(LocalDate start, LocalDate end) {
        return complaintRepository.getComplaintParetoByDate(start, end);
    }
}