package com.otboo.domain.feed.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.otboo.domain.clothes.entity.Clothes;
import com.otboo.domain.clothes.repository.ClothesRepository;
import com.otboo.domain.feed.dto.request.FeedCreateRequest;
import com.otboo.domain.feed.dto.request.FeedUpdateRequest;
import com.otboo.domain.feed.dto.response.FeedDto;
import com.otboo.domain.feed.entity.Feed;
import com.otboo.domain.feed.entity.FeedClothes;
import com.otboo.domain.feed.exception.FeedForbiddenException;
import com.otboo.domain.feed.exception.FeedUserNotFoundException;
import com.otboo.domain.feed.exception.FeedWeatherNotFoundException;
import com.otboo.domain.feed.mapper.FeedMapper;
import com.otboo.domain.feed.repository.FeedClothesRepository;
import com.otboo.domain.feed.repository.FeedRepository;
import com.otboo.domain.user.entity.User;
import com.otboo.domain.user.repository.UserRepository;
import com.otboo.domain.weather.entity.Weather;
import com.otboo.domain.weather.repository.WeatherRepository;
import java.util.List;
import java.util.UUID;
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
  private final WeatherRepository weatherRepository;
  // JSON 변환용
  private final ObjectMapper objectMapper;

  @Transactional
  public FeedDto createFeed(FeedCreateRequest request, UUID currentUserId){
    return null;
  }

  @Transactional
  public FeedDto updateFeed(UUID feedId, FeedUpdateRequest request, UUID currentUserId){
    return null;
  }

  @Transactional
  public void deleteFeed(UUID feedId, UUID currentUserId){
  }

}
