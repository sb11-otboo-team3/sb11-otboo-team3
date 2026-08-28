package com.otboo.domain.feed.core.search;

import com.otboo.domain.feed.core.dto.request.SortBy;
import com.otboo.domain.feed.core.dto.request.SortDirection;
import com.otboo.domain.feed.core.entity.Feed;
import com.otboo.domain.weather.entity.PrecipitationType;
import com.otboo.domain.weather.entity.SkyStatus;
import java.util.List;
import java.util.UUID;

public interface FeedSearchService {

  void index(Feed feed);

  void indexAll(List<Feed> feeds);

  void indexById(UUID feedId);

  void delete(UUID feedId);

  FeedSearchResult search(
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
}