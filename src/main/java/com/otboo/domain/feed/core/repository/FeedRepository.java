package com.otboo.domain.feed.core.repository;

import com.otboo.domain.feed.core.entity.Feed;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeedRepository extends JpaRepository<Feed, UUID>, FeedRepositoryCustom {

  Optional<Feed> findByIdAndDeletedAtIsNull(UUID feedId);

  Slice<Feed> findByDeletedAtIsNull(Pageable pageable);
}
