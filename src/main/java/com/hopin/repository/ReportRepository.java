package com.hopin.repository;

import com.hopin.model.Report;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportRepository extends JpaRepository<Report, Long> {
    List<Report> findAllByOrderByIdDesc();

    long countByStatus(String status);

    long countBySosTrueAndStatus(String status);
}
