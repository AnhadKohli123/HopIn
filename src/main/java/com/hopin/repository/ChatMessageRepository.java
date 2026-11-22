package com.hopin.repository;

import com.hopin.model.ChatMessage;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findByRide_IdAndRider_IdOrderByIdAsc(Long rideId, Long riderId);

    List<ChatMessage> findByRide_Driver_IdOrRider_IdOrderByIdDesc(Long driverId, Long riderId);

    boolean existsByRide_IdAndRider_Id(Long rideId, Long riderId);

    long countByRide_Driver_IdAndSender_IdNotAndSeenFalse(Long driverId, Long senderId);

    long countByRider_IdAndSender_IdNotAndSeenFalse(Long riderId, Long senderId);
}
