package com.otboo.domain.user.repository;

import com.otboo.domain.user.entity.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, UUID>, UserRepositoryCustom {

  Optional<User> findByEmail(String email);

  boolean existsByEmail(String email);

  @Modifying
  @Query("UPDATE User u SET u.tokenVersion = u.tokenVersion + 1 WHERE u.id = :userId")
  int incrementTokenVersion(@Param("userId") UUID userId);
}