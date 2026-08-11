package com.otboo.domain.feed.like.repository;

import com.otboo.domain.feed.like.entity.FeedLike;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeedLikeRepository extends JpaRepository<FeedLike, UUID>, FeedLikeRepositoryCustom {
  boolean existsByFeedIdAndUserId(UUID feedId, UUID userId);

  Optional<FeedLike> findByFeedIdAndUserId(UUID feedId, UUID userId);

  List<FeedLike> findByFeedIdInAndUserId(Collection<UUID> feedIds, UUID userId);
}
