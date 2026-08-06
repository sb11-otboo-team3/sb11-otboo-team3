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
import com.otboo.domain.feed.dto.request.FeedCreateRequest;
import com.otboo.domain.feed.dto.request.FeedUpdateRequest;
import com.otboo.domain.feed.dto.response.FeedDto;
import com.otboo.domain.feed.entity.Feed;
import com.otboo.domain.feed.entity.FeedClothes;
import com.otboo.domain.feed.exception.FeedClothesNotFoundException;
import com.otboo.domain.feed.exception.FeedForbiddenException;
import com.otboo.domain.feed.exception.FeedNotFoundException;
import com.otboo.domain.feed.exception.FeedUserNotFoundException;
import com.otboo.domain.feed.exception.FeedWeatherNotFoundException;
import com.otboo.domain.feed.mapper.FeedMapper;
import com.otboo.domain.feed.repository.FeedClothesRepository;
import com.otboo.domain.feed.repository.FeedRepository;
import com.otboo.domain.user.entity.User;
import com.otboo.domain.user.repository.UserRepository;
import com.otboo.domain.weather.dto.WeatherSummaryDto;
import com.otboo.domain.weather.entity.Weather;
import com.otboo.domain.weather.repository.WeatherRepository;
import com.otboo.domain.weather.service.WeatherService;
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
  private final WeatherService weatherService;
  private final WeatherRepository weatherRepository;
  private final ClothesAttributeRepository clothesAttributeRepository;
  private final AttributeSelectableValueRepository attributeSelectableValueRepository;
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

    WeatherSummaryDto weatherSummary = weatherService.getWeatherSummary(request.weatherId());
    JsonNode weatherSnapshot = objectMapper.valueToTree(weatherSummary);

    Feed feed = Feed.create(author, weather, weatherSnapshot, request.content());
    Feed savedFeed = feedRepository.save(feed);

    List<FeedClothes> feedClothes = clothes.stream()
        .map(cloth -> FeedClothes.create(savedFeed, cloth))
        .toList();

    feedClothesRepository.saveAll(feedClothes);

    // TODO: 피드 좋아요 기능 구현 후 현재 사용자의 좋아요 여부를 조회해 likedByMe에 반영
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

    // TODO: 피드 좋아요 기능 구현 후 현재 사용자의 좋아요 여부를 조회해 likedByMe에 반영
    boolean likedByMe = false;

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
}
