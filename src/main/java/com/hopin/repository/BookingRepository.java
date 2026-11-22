package com.hopin.repository;

import com.hopin.model.Booking;
import com.hopin.model.BookingStatus;
import com.hopin.model.RideStatus;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingRepository extends JpaRepository<Booking, Long> {
    List<Booking> findByRiderIdOrderByCreatedAtDesc(Long riderId);

    List<Booking> findByRideId(Long rideId);

    List<Booking> findByRideDriverIdOrderByCreatedAtDesc(Long driverId);

    boolean existsByRideIdAndRiderIdAndStatusIn(Long rideId, Long riderId, Collection<BookingStatus> statuses);

    long countByRide_Driver_IdAndStatusAndRide_Status(Long driverId, BookingStatus status, RideStatus rideStatus);

    long countByRider_IdAndStatusAndRide_Status(Long riderId, BookingStatus status, RideStatus rideStatus);
}
