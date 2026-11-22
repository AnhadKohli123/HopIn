package com.hopin.repository;

import com.hopin.model.TripShare;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TripShareRepository extends JpaRepository<TripShare, Long> {
    Optional<TripShare> findByToken(String token);

    Optional<TripShare> findFirstByRideIdAndSharerIdAndActiveTrue(Long rideId, Long sharerId);

    List<TripShare> findBySharerIdAndActiveTrue(Long sharerId);
}
