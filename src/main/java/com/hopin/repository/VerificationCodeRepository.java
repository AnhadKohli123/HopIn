package com.hopin.repository;

import com.hopin.model.VerificationCode;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VerificationCodeRepository extends JpaRepository<VerificationCode, Long> {
    Optional<VerificationCode> findTopByUserIdAndChannelOrderByIdDesc(Long userId, String channel);
}
