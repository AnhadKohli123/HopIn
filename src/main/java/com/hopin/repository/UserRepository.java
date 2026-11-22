package com.hopin.repository;

import com.hopin.model.Role;
import com.hopin.model.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);

    List<User> findByRole(Role role);

    boolean existsByPhoneAndPhoneVerifiedTrueAndIdNot(String phone, Long id);

    boolean existsByIdHashAndIdNot(String idHash, Long id);
}
