package com.hopin.repository;

import com.hopin.model.Ride;
import com.hopin.model.RideStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RideRepository extends JpaRepository<Ride, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Ride r where r.id = :id")
    Optional<Ride> findByIdForUpdate(@Param("id") Long id);

    List<Ride> findByDriverIdOrderByDepartTimeDesc(Long driverId);

    List<Ride> findByStatusAndSeatsLeftGreaterThanEqualAndDepartTimeBetween(
            RideStatus status, int seats, LocalDateTime fromTime, LocalDateTime toTime);

    long countByDriverId(Long driverId);

    long countByDriverIdAndStatus(Long driverId, RideStatus status);
}
