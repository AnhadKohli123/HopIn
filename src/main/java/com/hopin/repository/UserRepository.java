package com.hopin.repository;

import com.hopin.model.Role;
import com.hopin.model.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);

    List<User> findByIdStatusOrderByIdAsc(String idStatus);

    List<User> findByRole(Role role);

    long countByIdStatus(String idStatus);

    boolean existsByPhoneAndPhoneVerifiedTrueAndIdNot(String phone, Long id);
}
