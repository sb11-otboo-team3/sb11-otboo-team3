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
import com.otboo.domain.feed.comment.exception.InvalidFeedCommentCursorException;
import com.otboo.domain.feed.core.dto.request.SortBy;
import com.otboo.domain.feed.core.dto.request.SortDirection;
import com.otboo.domain.feed.core.dto.response.FeedDto;
import com.otboo.domain.feed.core.dto.response.FeedDtoCursorResponse;
import com.otboo.domain.feed.core.entity.Feed;
import com.otboo.domain.feed.core.mapper.FeedMapper;
import com.otboo.domain.feed.core.repository.FeedRepository;
import com.otboo.domain.feed.like.repository.FeedLikeRepository;
import com.otboo.domain.weather.dto.WeatherSummaryDto;
import com.otboo.domain.weather.entity.PrecipitationType;
import com.otboo.domain.weather.entity.SkyStatus;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
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
    validateCursor(cursor, idAfter);

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

    return new FeedDtoCursorResponse(
        data,
        nextCursor,
        nextIdAfter,
        hasNext,
        totalCount,
        sortBy.name(),
        sortDirection.name()
    );
  }

  // cursor와 idAfter는 둘 다 있거나 둘 다 없어야 함
  private void validateCursor(String cursor, UUID idAfter) {
    boolean hasCursor = cursor != null && !cursor.isBlank();
    boolean hasIdAfter = idAfter != null;

    if (hasCursor != hasIdAfter) {
      throw new InvalidFeedCommentCursorException();
    }

    if (hasCursor) {
      try {
        Instant.parse(cursor);
      } catch (DateTimeParseException exception) {
        throw new InvalidFeedCommentCursorException();
      }
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
