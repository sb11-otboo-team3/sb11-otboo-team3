package com.otboo.domain.user.repository;

import com.otboo.domain.user.entity.User;
import com.otboo.domain.user.entity.UserRole;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID>, UserRepositoryCustom {
  Optional<User> findByEmail(String email);
  boolean existsByEmail(String email);
  boolean existsByRole(UserRole role);
}