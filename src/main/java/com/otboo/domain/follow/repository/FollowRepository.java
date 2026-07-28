package com.otboo.domain.follow.repository;

import com.otboo.domain.follow.entity.Follow;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FollowRepository extends JpaRepository<Follow, UUID> {

  // 존재하는 유저 UUID인지 검사
  boolean existsByFollowerIdAndFolloweeId(UUID followerId, UUID followeeId);
}
