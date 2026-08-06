package com.otboo.domain.feed.repository;

import java.util.UUID;

public interface FeedRepositoryCustom {

  long deleteFeedsDeletedBeforeOneDay();

  long increaseLikeCount(UUID feedId);

  long decreaseLikeCount(UUID feedId);

  long increaseCommentCount(UUID feedId);
}
