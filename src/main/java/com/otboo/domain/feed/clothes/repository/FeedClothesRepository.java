package com.otboo.domain.feed.clothes.repository;

import com.otboo.domain.feed.core.entity.Feed;
import com.otboo.domain.feed.clothes.entity.FeedClothes;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeedClothesRepository extends JpaRepository<FeedClothes, UUID> {

  List<FeedClothes> findByFeed(Feed feed);

  List<FeedClothes> findByFeedAndClothesDeletedAtIsNull(Feed feed);

  List<FeedClothes> findByFeedInAndClothesDeletedAtIsNull(Collection<Feed> feeds);
}
