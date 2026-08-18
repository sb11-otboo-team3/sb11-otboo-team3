package com.otboo.domain.feed.core.cache;

import com.otboo.domain.feed.core.dto.response.FeedDtoCursorResponse;
import java.util.Optional;
import java.util.UUID;

public interface FeedAuthorListCache {

  Optional<FeedDtoCursorResponse> findAuthorFeeds(
      UUID authorId,
      UUID currentUserId,
      String cursor,
      UUID idAfter,
      int limit
  );

  void saveAuthorFeeds(
      UUID authorId,
      UUID currentUserId,
      String cursor,
      UUID idAfter,
      int limit,
      FeedDtoCursorResponse response
  );

  void evictAuthorFeeds(UUID authorId);
}