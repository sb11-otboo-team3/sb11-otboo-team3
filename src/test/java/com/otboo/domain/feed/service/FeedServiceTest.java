package com.otboo.domain.feed.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.otboo.domain.clothes.entity.Clothes;
import com.otboo.domain.clothes.repository.ClothesRepository;
import com.otboo.domain.feed.dto.request.FeedCreateRequest;
import com.otboo.domain.feed.dto.request.FeedUpdateRequest;
import com.otboo.domain.feed.dto.response.FeedDto;
import com.otboo.domain.feed.entity.Feed;
import com.otboo.domain.feed.entity.FeedClothes;
import com.otboo.domain.feed.repository.FeedClothesRepository;
import com.otboo.domain.feed.repository.FeedRepository;
import com.otboo.domain.user.entity.User;
import com.otboo.domain.user.repository.UserRepository;
import com.otboo.domain.weather.entity.Weather;
import com.otboo.domain.weather.repository.WeatherRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class FeedServiceTest {

  @Mock
  private FeedRepository feedRepository;

  @Mock
  private FeedClothesRepository feedClothesRepository;

  @Mock
  private UserRepository userRepository;

  @Mock
  private ClothesRepository clothesRepository;

  @Mock
  private WeatherRepository weatherRepository;

  @InjectMocks
  private FeedService feedService;

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  @DisplayName("피드 생성 성공 테스트")
  void createFeed_success() {
    UUID authorId = UUID.randomUUID();
    UUID weatherId = UUID.randomUUID();
    UUID clothesId = UUID.randomUUID();
    UUID feedId = UUID.randomUUID();

    User author = User.create("author@test.com", "author", "password");
    ReflectionTestUtils.setField(author, "id", authorId);

    Weather weather = mock(Weather.class);
    given(weather.getId()).willReturn(weatherId);

    Clothes clothes = mock(Clothes.class);
    given(clothes.getId()).willReturn(clothesId);

    FeedCreateRequest request = new FeedCreateRequest(
        authorId,
        weatherId,
        List.of(clothesId),
        "오늘의 피드"
    );

    given(userRepository.findById(authorId)).willReturn(Optional.of(author));
    given(weatherRepository.findById(weatherId)).willReturn(Optional.of(weather));
    given(clothesRepository.findAllById(List.of(clothesId))).willReturn(List.of(clothes));
    given(feedRepository.save(any(Feed.class))).willAnswer(invocation -> {
      Feed feed = invocation.getArgument(0);
      ReflectionTestUtils.setField(feed, "id", feedId);
      return feed;
    });
    given(feedClothesRepository.save(any(FeedClothes.class)))
        .willAnswer(invocation -> invocation.getArgument(0));

    FeedDto result = feedService.createFeed(request, authorId);

    assertThat(result).isNotNull();
    assertThat(result.id()).isEqualTo(feedId);
    assertThat(result.content()).isEqualTo("오늘의 피드");

    verify(feedRepository).save(any(Feed.class));
    verify(feedClothesRepository).save(any(FeedClothes.class));
  }

  @Test
  @DisplayName("피드 수정 성공 테스트")
  void updateFeed_success() {
    UUID authorId = UUID.randomUUID();

    UUID feedId = UUID.randomUUID();

    User author = User.create("author@test.com", "author", "password");
    ReflectionTestUtils.setField(author, "id", authorId);

    Weather weather = mock(Weather.class);
    ReflectionTestUtils.setField(weather, "id", UUID.randomUUID());

    Feed feed = Feed.create(
        author,
        weather,
        objectMapper.createObjectNode(),
        "수정 전 내용"
    );

    ReflectionTestUtils.setField(feed, "id", feedId);

    FeedUpdateRequest request = new FeedUpdateRequest("수정 후 내용");

    given(feedRepository.findById(feedId)).willReturn(Optional.of(feed));

    FeedDto result = feedService.updateFeed(feedId, request, authorId);

    assertThat(result).isNotNull();
    assertThat(result.id()).isEqualTo(feedId);
    assertThat(result.content()).isEqualTo("수정 후 내용");

    verify(feedRepository).findById(feedId);
  }

  @Test
  @DisplayName("피드 삭제 성공 테스트")
  void deleteFeed_success() {
    UUID authorId = UUID.randomUUID();

    UUID feedId = UUID.randomUUID();

    User author = User.create("author@test.com", "author", "password");
    ReflectionTestUtils.setField(author, "id", authorId);

    Weather weather = mock(Weather.class);

    Feed feed = Feed.create(
        author,
        weather,
        objectMapper.createObjectNode(),
        "삭제할 피드"
    );
    ReflectionTestUtils.setField(feed, "id", feedId);

    given(feedRepository.findById(feedId)).willReturn(Optional.of(feed));

    feedService.deleteFeed(feedId, authorId);

    assertThat(feed.getDeletedAt()).isNotNull();

    verify(feedRepository).findById(feedId);
  }
}