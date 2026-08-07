package com.otboo.domain.feed.repository;

import com.otboo.domain.feed.entity.FeedLike;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeedLikeRepository extends JpaRepository<FeedLike, UUID> {
  boolean existsByFeedIdAndUserId(UUID feedId, UUID userId);

  Optional<FeedLike> findByFeedIdAndUserId(UUID feedId, UUID userId);
}
