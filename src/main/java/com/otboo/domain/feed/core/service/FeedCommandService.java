package com.otboo.domain.feed.core.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.otboo.domain.clothes.entity.AttributeSelectableValue;
import com.otboo.domain.clothes.entity.Clothes;
import com.otboo.domain.clothes.entity.ClothesAttribute;
import com.otboo.domain.clothes.entity.ClothesAttributeDefinition;
import com.otboo.domain.clothes.repository.AttributeSelectableValueRepository;
import com.otboo.domain.clothes.repository.ClothesAttributeRepository;
import com.otboo.domain.clothes.repository.ClothesRepository;
import com.otboo.domain.feed.clothes.entity.FeedClothes;
import com.otboo.domain.feed.clothes.exception.FeedClothesNotFoundException;
import com.otboo.domain.feed.clothes.repository.FeedClothesRepository;
import com.otboo.domain.feed.core.cache.FeedAuthorListCache;
import com.otboo.domain.feed.core.dto.request.FeedCreateRequest;
import com.otboo.domain.feed.core.dto.request.FeedUpdateRequest;
import com.otboo.domain.feed.core.dto.response.FeedDto;
import com.otboo.domain.feed.core.entity.Feed;
import com.otboo.domain.feed.core.exception.FeedForbiddenException;
import com.otboo.domain.feed.core.exception.FeedNotFoundException;
import com.otboo.domain.feed.core.exception.FeedUserNotFoundException;
import com.otboo.domain.feed.core.exception.FeedWeatherNotFoundException;
import com.otboo.domain.feed.core.mapper.FeedMapper;
import com.otboo.domain.feed.core.repository.FeedRepository;
import com.otboo.domain.feed.core.search.FeedSearchService;
import com.otboo.domain.feed.like.repository.FeedLikeRepository;
import com.otboo.domain.follow.repository.FollowRepository;
import com.otboo.domain.notification.entity.NotificationLevel;
import com.otboo.domain.notification.event.NotificationEvent;
import com.otboo.domain.user.entity.User;
import com.otboo.domain.user.repository.UserRepository;
import com.otboo.domain.weather.dto.WeatherSummaryDto;
import com.otboo.domain.weather.entity.Weather;
import com.otboo.domain.weather.repository.WeatherRepository;
import com.otboo.domain.weather.service.WeatherSummaryFinder;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FeedCommandService {

  private final FeedRepository feedRepository;
  private final FeedClothesRepository feedClothesRepository;
  private final UserRepository userRepository;
  private final ClothesRepository clothesRepository;
  private final WeatherSummaryFinder weatherSummaryFinder;
  private final WeatherRepository weatherRepository;
  private final ClothesAttributeRepository clothesAttributeRepository;
  private final AttributeSelectableValueRepository attributeSelectableValueRepository;
  private final FeedLikeRepository feedLikeRepository;
  private final ApplicationEventPublisher eventPublisher;
  private final FollowRepository followRepository;
  private final FeedAuthorListCache feedAuthorListCache;
  private final FeedSearchService feedSearchService;
  private final FeedMapper feedMapper;
  private final ObjectMapper objectMapper;


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

    evictAuthorFeedsAfterCommit(author.getId());
    indexFeedAfterCommit(savedFeed);

    followRepository.findFollowerIdsByFolloweeId(author.getId()).stream()
        .filter(followerId -> !followerId.equals(author.getId()))
        .forEach(followerId ->
            eventPublisher.publishEvent(
                new NotificationEvent(
                    followerId,
                    "팔로우한 사용자가 피드를 등록했습니다.",
                    author.getName() + "님이 새 피드를 등록했습니다.",
                    NotificationLevel.INFO
                )
            )
        );
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

    evictAuthorFeedsAfterCommit(feed.getAuthor().getId());
    indexFeedAfterCommit(feed);

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

    evictAuthorFeedsAfterCommit(feed.getAuthor().getId());
    deleteFeedIndexAfterCommit(feedId);
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

  private void evictAuthorFeedsAfterCommit(UUID authorId) {
    Runnable evict = () -> feedAuthorListCache.evictAuthorFeeds(authorId);

    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override
        public void afterCommit() {
          evict.run();
        }
      });
      return;
    }

    evict.run();
  }

  private void indexFeedAfterCommit(Feed feed) {
    Runnable index = () -> feedSearchService.index(feed);

    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override
        public void afterCommit() {
          index.run();
        }
      });
      return;
    }

    index.run();
  }

  private void deleteFeedIndexAfterCommit(UUID feedId) {
    Runnable delete = () -> feedSearchService.delete(feedId);

    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override
        public void afterCommit() {
          delete.run();
        }
      });
      return;
    }

    delete.run();
  }
}
