package com.otboo.domain.feed.core.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.otboo.domain.clothes.entity.AttributeSelectableValue;
import com.otboo.domain.clothes.entity.Clothes;
import com.otboo.domain.clothes.entity.ClothesAttribute;
import com.otboo.domain.clothes.entity.ClothesAttributeDefinition;
import com.otboo.domain.clothes.repository.AttributeSelectableValueRepository;
import com.otboo.domain.clothes.repository.ClothesAttributeRepository;
import com.otboo.domain.feed.clothes.entity.FeedClothes;
import com.otboo.domain.feed.clothes.repository.FeedClothesRepository;
import com.otboo.domain.feed.core.cache.FeedAuthorListCache;
import com.otboo.domain.feed.core.dto.request.SortBy;
import com.otboo.domain.feed.core.dto.request.SortDirection;
import com.otboo.domain.feed.core.dto.response.FeedDto;
import com.otboo.domain.feed.core.dto.response.FeedDtoCursorResponse;
import com.otboo.domain.feed.core.entity.Feed;
import com.otboo.domain.feed.core.exception.InvalidFeedCursorException;
import com.otboo.domain.feed.core.mapper.FeedMapper;
import com.otboo.domain.feed.core.repository.FeedRepository;
import com.otboo.domain.feed.core.search.FeedSearchResult;
import com.otboo.domain.feed.core.search.FeedSearchService;
import com.otboo.domain.feed.like.repository.FeedLikeRepository;
import com.otboo.domain.weather.dto.WeatherSummaryDto;
import com.otboo.domain.weather.entity.PrecipitationType;
import com.otboo.domain.weather.entity.SkyStatus;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FeedQueryService {

  private final FeedRepository feedRepository;
  private final FeedClothesRepository feedClothesRepository;
  private final FeedLikeRepository feedLikeRepository;
  private final ClothesAttributeRepository clothesAttributeRepository;
  private final AttributeSelectableValueRepository attributeSelectableValueRepository;
  private final FeedAuthorListCache feedAuthorListCache;
  private final FeedSearchService feedSearchService;
  private final FeedMapper feedMapper;
  private final ObjectMapper objectMapper;

  public FeedDtoCursorResponse getFeeds(
      String cursor,
      UUID idAfter,
      int limit,
      SortBy sortBy,
      SortDirection sortDirection,
      String keywordLike,
      SkyStatus skyStatusEqual,
      PrecipitationType precipitationTypeEqual,
      UUID authorIdEqual,
      UUID currentUserId
  ) {
    validateCursor(cursor, idAfter, sortBy);

    boolean cacheableAuthorFeeds = authorIdEqual != null
        && keywordLike == null
        && skyStatusEqual == null
        && precipitationTypeEqual == null
        && sortBy == SortBy.createdAt
        && sortDirection == SortDirection.DESCENDING;

    if (cacheableAuthorFeeds) {
      Optional<FeedDtoCursorResponse> cachedResponse =
          feedAuthorListCache.findAuthorFeeds(
              authorIdEqual,
              currentUserId,
              cursor,
              idAfter,
              limit
          );

      if (cachedResponse.isPresent()) {
        return cachedResponse.get();
      }
    }

    if (keywordLike != null && !keywordLike.isBlank()) {
      try {
        return getFeedsBySearch(
            cursor,
            idAfter,
            limit,
            sortBy,
            sortDirection,
            keywordLike,
            skyStatusEqual,
            precipitationTypeEqual,
            authorIdEqual,
            currentUserId
        );
      } catch (IllegalStateException exception) {
        // Elasticsearch 검색 실패 시 기존 DB 검색으로 fallback
      }
    }

    List<Feed> feeds = feedRepository.findFeeds(
        cursor,
        idAfter,
        limit + 1,
        sortBy,
        sortDirection,
        keywordLike,
        skyStatusEqual,
        precipitationTypeEqual,
        authorIdEqual
    );

    boolean hasNext = feeds.size() > limit;

    if (hasNext) {
      feeds = feeds.subList(0, limit);
    }

    List<FeedDto> data = toFeedDtos(feeds, currentUserId);

    String nextCursor = null;
    UUID nextIdAfter = null;

    if (hasNext) {
      Feed last = feeds.get(feeds.size() - 1);

      if (sortBy == SortBy.createdAt) {
        nextCursor = last.getCreatedAt().toString();
      }

      if (sortBy == SortBy.likeCount) {
        nextCursor = String.valueOf(last.getLikeCount());
      }

      nextIdAfter = last.getId();
    }

    long totalCount = feedRepository.countFeeds(
        keywordLike,
        skyStatusEqual,
        precipitationTypeEqual,
        authorIdEqual
    );

    FeedDtoCursorResponse response = new FeedDtoCursorResponse(
        data,
        nextCursor,
        nextIdAfter,
        hasNext,
        totalCount,
        sortBy.name(),
        sortDirection.name()
    );

    if (cacheableAuthorFeeds) {
      feedAuthorListCache.saveAuthorFeeds(
          authorIdEqual,
          currentUserId,
          cursor,
          idAfter,
          limit,
          response
      );
    }

    return response;
  }

  private FeedDtoCursorResponse getFeedsBySearch(
      String cursor,
      UUID idAfter,
      int limit,
      SortBy sortBy,
      SortDirection sortDirection,
      String keywordLike,
      SkyStatus skyStatusEqual,
      PrecipitationType precipitationTypeEqual,
      UUID authorIdEqual,
      UUID currentUserId
  ) {
    FeedSearchResult searchResult = feedSearchService.search(
        cursor,
        idAfter,
        limit,
        sortBy,
        sortDirection,
        keywordLike,
        skyStatusEqual,
        precipitationTypeEqual,
        authorIdEqual
    );

    List<Feed> feeds = feedRepository.findFeedsByIds(searchResult.feedIds());

    if (feeds.size() != searchResult.feedIds().size()) {
      throw new IllegalStateException("Elasticsearch 검색 결과와 DB 조회 결과가 일치하지 않습니다.");
    }

    feeds = sortBySearchResultOrder(feeds, searchResult.feedIds());

    List<FeedDto> data = toFeedDtos(feeds, currentUserId);

    return new FeedDtoCursorResponse(
        data,
        searchResult.nextCursor(),
        searchResult.nextIdAfter(),
        searchResult.hasNext(),
        searchResult.totalCount(),
        sortBy.name(),
        sortDirection.name()
    );
  }

  private List<Feed> sortBySearchResultOrder(List<Feed> feeds, List<UUID> feedIds) {
    Map<UUID, Integer> orderByFeedId = new HashMap<>();

    for (int i = 0; i < feedIds.size(); i++) {
      orderByFeedId.put(feedIds.get(i), i);
    }

    return feeds.stream()
        .sorted(Comparator.comparing(feed -> orderByFeedId.get(feed.getId())))
        .toList();
  }

  private void validateCursor(String cursor, UUID idAfter, SortBy sortBy) {
    boolean hasCursor = cursor != null && !cursor.isBlank();
    boolean hasIdAfter = idAfter != null;

    if (hasCursor != hasIdAfter) {
      throw new InvalidFeedCursorException();
    }

    if (!hasCursor) {
      return;
    }

    try {
      if (sortBy == SortBy.createdAt) {
        Instant.parse(cursor);
        return;
      }

      if (sortBy == SortBy.likeCount) {
        Long.parseLong(cursor);
        return;
      }

      throw new InvalidFeedCursorException();
    } catch (DateTimeParseException | NumberFormatException exception) {
      throw new InvalidFeedCursorException();
    }
  }

  private List<FeedDto> toFeedDtos(List<Feed> feeds, UUID currentUserId) {
    if (feeds.isEmpty()) {
      return List.of();
    }

    List<UUID> feedIds = feeds.stream()
        .map(Feed::getId)
        .toList();

    List<UUID> likedFeedIds = feedLikeRepository
        .findByFeedIdInAndUserId(feedIds, currentUserId)
        .stream()
        .map(feedLike -> feedLike.getFeed().getId())
        .toList();

    List<FeedClothes> allFeedClothes =
        feedClothesRepository.findByFeedInAndClothesDeletedAtIsNull(feeds);

    Map<UUID, List<FeedClothes>> feedClothesByFeedId = allFeedClothes.stream()
        .collect(Collectors.groupingBy(feedClothes -> feedClothes.getFeed().getId()));

    List<Clothes> clothes = allFeedClothes.stream()
        .map(FeedClothes::getClothes)
        .distinct()
        .toList();

    List<ClothesAttribute> attributes = clothesAttributeRepository.findByClothesIn(clothes);

    Map<UUID, List<ClothesAttribute>> attributesByClothesId = attributes.stream()
        .collect(Collectors.groupingBy(attribute -> attribute.getClothes().getId()));

    List<ClothesAttributeDefinition> definitions = attributes.stream()
        .map(ClothesAttribute::getDefinition)
        .distinct()
        .toList();

    List<AttributeSelectableValue> selectableValues =
        attributeSelectableValueRepository
            .findByDefinitionInAndDeletedAtIsNullOrderByDisplayOrderAsc(definitions);

    Map<UUID, List<String>> selectableValuesByDefinitionId = selectableValues.stream()
        .collect(Collectors.groupingBy(
            value -> value.getDefinition().getId(),
            Collectors.mapping(AttributeSelectableValue::getValue, Collectors.toList())
        ));

    return feeds.stream()
        .map(feed -> feedMapper.toDto(
            feed,
            objectMapper.convertValue(feed.getWeatherSnapshot(), WeatherSummaryDto.class),
            feedClothesByFeedId.getOrDefault(feed.getId(), List.of()),
            attributesByClothesId,
            selectableValuesByDefinitionId,
            likedFeedIds.contains(feed.getId())
        ))
        .toList();
  }
}