package com.otboo.domain.feed.repository;

import com.otboo.domain.feed.dto.request.SortBy;
import com.otboo.domain.feed.dto.request.SortDirection;
import com.otboo.domain.feed.entity.Feed;
import com.otboo.domain.weather.entity.PrecipitationType;
import com.otboo.domain.weather.entity.SkyStatus;
import java.util.List;
import java.util.UUID;

public interface FeedRepositoryCustom {

  long deleteFeedsDeletedBeforeOneDay();

  long increaseLikeCount(UUID feedId);

  long decreaseLikeCount(UUID feedId);

  long increaseCommentCount(UUID feedId);

  List<Feed> findFeeds(
      String cursor,
      UUID idAfter,
      int limit,
      SortBy sortBy,
      SortDirection sortDirection,
      String keywordLike,
      SkyStatus skyStatusEqual,
      PrecipitationType precipitationTypeEqual,
      UUID authorIdEqual
  );

  long countFeeds(
      String keywordLike,
      SkyStatus skyStatusEqual,
      PrecipitationType precipitationTypeEqual,
      UUID authorIdEqual
  );
}
