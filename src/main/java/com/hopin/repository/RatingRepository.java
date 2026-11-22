package com.hopin.repository;

import com.hopin.model.Rating;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RatingRepository extends JpaRepository<Rating, Long> {
    boolean existsByRideIdAndFromUserIdAndToUserId(Long rideId, Long fromId, Long toId);

    List<Rating> findByToUserIdOrderByIdDesc(Long toId);

    List<Rating> findTop5ByToUserIdOrderByIdDesc(Long toId);

    List<Rating> findByFromUserId(Long fromId);
}
