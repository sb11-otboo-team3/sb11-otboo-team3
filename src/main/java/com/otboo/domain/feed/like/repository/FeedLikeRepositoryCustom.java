package com.otboo.domain.feed.like.repository;

import java.util.UUID;

public interface FeedLikeRepositoryCustom {

  long deleteByFeedIdAndUserId(UUID feedId, UUID userId);
}
