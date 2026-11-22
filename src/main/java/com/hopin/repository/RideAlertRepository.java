package com.hopin.repository;

import com.hopin.model.RideAlert;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RideAlertRepository extends JpaRepository<RideAlert, Long> {
    List<RideAlert> findByUserIdOrderByIdDesc(Long userId);

    long countByUserId(Long userId);
}
