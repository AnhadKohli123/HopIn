package com.hopin.repository;

import com.hopin.model.Notification;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findTop30ByUserIdOrderByIdDesc(Long userId);

    List<Notification> findByUserIdAndSeenFalse(Long userId);

    long countByUserIdAndSeenFalse(Long userId);
}
