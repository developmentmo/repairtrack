package com.repairtrack.security.infrastructure;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.repairtrack.security.domain.User;

public interface UserRepository extends JpaRepository<User, UUID> {

    /** Expects an already normalized email (see {@link User#normalizeEmail(String)}). */
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}
