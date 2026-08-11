package com.otboo.domain.feed.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.otboo.domain.clothes.entity.AttributeSelectableValue;
import com.otboo.domain.clothes.entity.Clothes;
import com.otboo.domain.clothes.entity.ClothesAttribute;
import com.otboo.domain.clothes.entity.ClothesAttributeDefinition;
import com.otboo.domain.clothes.repository.AttributeSelectableValueRepository;
import com.otboo.domain.clothes.repository.ClothesAttributeRepository;
import com.otboo.domain.clothes.repository.ClothesRepository;
import com.otboo.domain.feed.dto.request.FeedCommentCreateRequest;
import com.otboo.domain.feed.dto.request.FeedCreateRequest;
import com.otboo.domain.feed.dto.request.FeedUpdateRequest;
import com.otboo.domain.feed.dto.request.SortBy;
import com.otboo.domain.feed.dto.request.SortDirection;
import com.otboo.domain.feed.dto.response.FeedCommentDto;
import com.otboo.domain.feed.dto.response.FeedCommentDtoCursorResponse;
import com.otboo.domain.feed.dto.response.FeedDto;
import com.otboo.domain.feed.dto.response.FeedDtoCursorResponse;
import com.otboo.domain.feed.entity.Comment;
import com.otboo.domain.feed.entity.Feed;
import com.otboo.domain.feed.entity.FeedClothes;
import com.otboo.domain.feed.entity.FeedLike;
import com.otboo.domain.feed.exception.FeedClothesNotFoundException;
import com.otboo.domain.feed.exception.FeedCommentForbiddenException;
import com.otboo.domain.feed.exception.FeedForbiddenException;
import com.otboo.domain.feed.exception.DuplicateFeedLikeException;
import com.otboo.domain.feed.exception.FeedLikeNotFoundException;
import com.otboo.domain.feed.exception.FeedNotFoundException;
import com.otboo.domain.feed.exception.FeedUserNotFoundException;
import com.otboo.domain.feed.exception.FeedWeatherNotFoundException;
import com.otboo.domain.feed.exception.InvalidFeedCommentCursorException;
import com.otboo.domain.feed.exception.InvalidFeedCommentRequestException;
import com.otboo.domain.feed.mapper.FeedCommentMapper;
import com.otboo.domain.feed.mapper.FeedMapper;
import com.otboo.domain.feed.repository.FeedClothesRepository;
import com.otboo.domain.feed.repository.FeedCommentRepository;
import com.otboo.domain.feed.repository.FeedLikeRepository;
import com.otboo.domain.feed.repository.FeedRepository;
import com.otboo.domain.user.entity.User;
import com.otboo.domain.user.repository.UserRepository;
import com.otboo.domain.weather.dto.WeatherSummaryDto;
import com.otboo.domain.weather.entity.PrecipitationType;
import com.otboo.domain.weather.entity.SkyStatus;
import com.otboo.domain.weather.entity.Weather;
import com.otboo.domain.weather.repository.WeatherRepository;
import com.otboo.domain.weather.service.WeatherSummaryFinder;
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
public class FeedService {

  private final FeedRepository feedRepository;
  private final FeedClothesRepository feedClothesRepository;
  private final UserRepository userRepository;
  private final ClothesRepository clothesRepository;
  private final WeatherSummaryFinder weatherSummaryFinder;
  private final WeatherRepository weatherRepository;
  private final ClothesAttributeRepository clothesAttributeRepository;
  private final AttributeSelectableValueRepository attributeSelectableValueRepository;
  private final FeedLikeRepository feedLikeRepository;
  private final FeedCommentRepository feedCommentRepository;
  private final FeedMapper feedMapper;
  private final ObjectMapper objectMapper;
  private final FeedCommentMapper feedCommentMapper;


  @Transactional
  public FeedDto createFeed(FeedCreateRequest request, UUID currentUserId) {
    if (!request.authorId().equals(currentUserId)) {
      throw new FeedForbiddenException();
    }

    User author = userRepository.findById(request.authorId())
        .orElseThrow(() -> new FeedUserNotFoundException(request.authorId()));

    Weather weather = weatherRepository.findById(request.weatherId())
        .orElseThrow(() -> new FeedWeatherNotFoundException(request.weatherId()));

    List<Clothes> clothes = clothesRepository.findByIdInAndDeletedAtIsNull(request.clothesIds());

    if (clothes.size() != request.clothesIds().size()) {
      throw new FeedClothesNotFoundException();
    }

    boolean hasNotOwnedClothes = clothes.stream()
        .anyMatch(cloth -> !cloth.getOwner().getId().equals(author.getId()));

    if (hasNotOwnedClothes) {
      throw new FeedForbiddenException();
    }

    WeatherSummaryDto weatherSummary = weatherSummaryFinder.find(request.weatherId());
    JsonNode weatherSnapshot = objectMapper.valueToTree(weatherSummary);

    Feed feed = Feed.create(author, weather, weatherSnapshot, request.content());
    Feed savedFeed = feedRepository.save(feed);

    List<FeedClothes> feedClothes = clothes.stream()
        .map(cloth -> FeedClothes.create(savedFeed, cloth))
        .toList();

    feedClothesRepository.saveAll(feedClothes);

    // 최초 생성시엔 좋아요를 누를 수가 없음
    boolean likedByMe = false;

    return toFeedDto(savedFeed, likedByMe);
  }

  @Transactional
  public FeedDto updateFeed(UUID feedId, FeedUpdateRequest request, UUID currentUserId) {
    Feed feed = feedRepository.findByIdAndDeletedAtIsNull(feedId)
        .orElseThrow(() -> new FeedNotFoundException(feedId));

    if (!feed.getAuthor().getId().equals(currentUserId)) {
      throw new FeedForbiddenException();
    }

    feed.updateContent(request.content());

    boolean likedByMe = feedLikeRepository.existsByFeedIdAndUserId(feedId, currentUserId);

    return toFeedDto(feed, likedByMe);
  }

  @Transactional
  public void deleteFeed(UUID feedId, UUID currentUserId) {
    Feed feed = feedRepository.findByIdAndDeletedAtIsNull(feedId)
        .orElseThrow(() -> new FeedNotFoundException(feedId));

    if (!feed.getAuthor().getId().equals(currentUserId)) {
      throw new FeedForbiddenException();
    }

    feed.delete();
  }

  private FeedDto toFeedDto(Feed feed, boolean likedByMe) {
    WeatherSummaryDto weatherSummary =
        objectMapper.convertValue(feed.getWeatherSnapshot(), WeatherSummaryDto.class);

    List<FeedClothes> feedClothes = feedClothesRepository.findByFeedAndClothesDeletedAtIsNull(feed);

    List<Clothes> clothes = feedClothes.stream()
        .map(FeedClothes::getClothes)
        .toList();

    List<ClothesAttribute> attributes =
        clothesAttributeRepository.findByClothesIn(clothes);

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

    return feedMapper.toDto(
        feed,
        weatherSummary,
        feedClothes,
        attributesByClothesId,
        selectableValuesByDefinitionId,
        likedByMe
    );
  }

  @Transactional
  public void createFeedLike(UUID feedId, UUID currentUserId) {
    User user = userRepository.findById(currentUserId)
        .orElseThrow(() -> new FeedUserNotFoundException(currentUserId));

    Feed feed = feedRepository.findByIdAndDeletedAtIsNull(feedId)
        .orElseThrow(() -> new FeedNotFoundException(feedId));

    if (!feedLikeRepository.existsByFeedIdAndUserId(feedId, currentUserId)) {
      FeedLike feedLike = FeedLike.create(feed, user);
      feedLikeRepository.save(feedLike);
      feedRepository.increaseLikeCount(feedId);
    } else {
      throw new DuplicateFeedLikeException();
    }
  }

  @Transactional
  public void deleteFeedLike(UUID feedId, UUID currentUserId) {
    userRepository.findById(currentUserId)
        .orElseThrow(() -> new FeedUserNotFoundException(currentUserId));

    Feed feed = feedRepository.findByIdAndDeletedAtIsNull(feedId)
        .orElseThrow(() -> new FeedNotFoundException(feedId));

    FeedLike feedLike = feedLikeRepository.findByFeedIdAndUserId(feedId, currentUserId)
        .orElseThrow(() -> new FeedLikeNotFoundException(feedId));

    feedLikeRepository.delete(feedLike);
    feedRepository.decreaseLikeCount(feedId);
  }

  @Transactional
  public FeedCommentDto createFeedComment(
      UUID feedId,
      FeedCommentCreateRequest request,
      UUID currentUserId
  ) {
    User user = userRepository.findById(currentUserId)
        .orElseThrow(() -> new FeedUserNotFoundException(currentUserId));

    Feed feed = feedRepository.findByIdAndDeletedAtIsNull(feedId)
        .orElseThrow(() -> new FeedNotFoundException(feedId));

    if (!feedId.equals(request.feedId())) {
      throw new InvalidFeedCommentRequestException();
    }

    if (!currentUserId.equals(request.authorId())) {
      throw new FeedCommentForbiddenException();
    }

    Comment comment = Comment.create(feed, user, request.content());
    Comment savedComment = feedCommentRepository.save(comment);

    feedRepository.increaseCommentCount(feedId);

    return feedCommentMapper.toDto(savedComment);
  }

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
  }

  public FeedCommentDtoCursorResponse getComment(
      UUID feedId,
      String cursor,
      UUID idAfter,
      int limit
  ) {
    validateCursor(cursor, idAfter);

    feedRepository.findByIdAndDeletedAtIsNull(feedId)
        .orElseThrow(() -> new FeedNotFoundException(feedId));

    List<Comment> comments = feedCommentRepository.findComments(
        feedId,
        cursor,
        idAfter,
        limit + 1
    );

    boolean hasNext = comments.size() > limit;

    if (hasNext) {
      comments = comments.subList(0, limit);
    }

    List<FeedCommentDto> data = comments.stream()
        .map(feedCommentMapper::toDto)
        .toList();

    String nextCursor = null;
    UUID nextIdAfter = null;

    if (hasNext) {
      Comment last = comments.get(comments.size() - 1);
      nextCursor = last.getCreatedAt().toString();
      nextIdAfter = last.getId();
    }

    long totalCount = feedCommentRepository.countComments(feedId);

    return new FeedCommentDtoCursorResponse(
        data,
        nextCursor,
        nextIdAfter,
        hasNext,
        totalCount,
        "createdAt",
        "ASCENDING"
    );
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
