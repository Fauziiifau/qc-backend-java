package com.gmp.defect_system.repository;

import com.gmp.defect_system.entity.CustomerComplaint;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;

/** Repository khusus export v2. */
public interface ExportCustomerComplaintRepository extends JpaRepository<CustomerComplaint, Long> {
    List<CustomerComplaint> findByComplaintDateBetweenOrderByIdAsc(LocalDate startDate, LocalDate endDate);
}
