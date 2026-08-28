package com.otboo.domain.feed.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;

import com.otboo.domain.feed.clothes.exception.FeedClothesNotFoundException;
import com.otboo.domain.feed.core.cache.FeedAuthorListCache;
import com.otboo.domain.feed.core.exception.FeedForbiddenException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.otboo.domain.clothes.entity.Clothes;
import com.otboo.domain.clothes.repository.AttributeSelectableValueRepository;
import com.otboo.domain.clothes.repository.ClothesAttributeRepository;
import com.otboo.domain.clothes.repository.ClothesRepository;
import com.otboo.domain.feed.clothes.repository.FeedClothesRepository;
import com.otboo.domain.feed.core.dto.request.FeedCreateRequest;
import com.otboo.domain.feed.core.dto.request.FeedUpdateRequest;
import com.otboo.domain.feed.core.dto.response.FeedDto;
import com.otboo.domain.feed.core.entity.Feed;
import com.otboo.domain.feed.core.mapper.FeedMapper;
import com.otboo.domain.feed.core.repository.FeedRepository;
import com.otboo.domain.feed.core.search.FeedSearchService;
import com.otboo.domain.feed.like.repository.FeedLikeRepository;
import com.otboo.domain.follow.repository.FollowRepository;
import com.otboo.domain.user.entity.User;
import com.otboo.domain.user.repository.UserRepository;
import com.otboo.domain.weather.dto.WeatherSummaryDto;
import com.otboo.domain.weather.entity.Weather;
import com.otboo.domain.weather.repository.WeatherRepository;
import com.otboo.domain.weather.service.WeatherSummaryFinder;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class FeedCommandServiceTest {

  @Mock
  private FeedRepository feedRepository;

  @Mock
  private FeedClothesRepository feedClothesRepository;

  @Mock
  private UserRepository userRepository;

  @Mock
  private ClothesRepository clothesRepository;

  @Mock
  private WeatherSummaryFinder weatherSummaryFinder;

  @Mock
  private WeatherRepository weatherRepository;

  @Mock
  private ClothesAttributeRepository clothesAttributeRepository;

  @Mock
  private AttributeSelectableValueRepository attributeSelectableValueRepository;

  @Mock
  private FeedLikeRepository feedLikeRepository;

  @Mock
  private FollowRepository followRepository;

  @Mock
  private ApplicationEventPublisher eventPublisher;

  @Mock
  private FeedMapper feedMapper;

  @Mock
  private FeedAuthorListCache feedAuthorListCache;

  @Mock
  private FeedSearchService feedSearchService;

  @Spy
  private ObjectMapper objectMapper = new ObjectMapper();

  @InjectMocks
  private FeedCommandService feedCommandService;

  @Test
  @DisplayName("피드 생성 성공")
  void createFeed_success() {
    UUID authorId = UUID.randomUUID();
    UUID weatherId = UUID.randomUUID();
    UUID clothesId = UUID.randomUUID();

    User author = User.create("author@test.com", "author", "password");
    ReflectionTestUtils.setField(author, "id", authorId);

    Weather weather = mock(Weather.class);
    Clothes clothes = mock(Clothes.class);
    WeatherSummaryDto weatherSummary = mock(WeatherSummaryDto.class);
    FeedDto feedDto = mock(FeedDto.class);

    given(clothes.getOwner()).willReturn(author);
    given(userRepository.findById(authorId)).willReturn(Optional.of(author));
    given(weatherRepository.findById(weatherId)).willReturn(Optional.of(weather));
    given(clothesRepository.findByIdInAndDeletedAtIsNull(List.of(clothesId)))
        .willReturn(List.of(clothes));
    given(weatherSummaryFinder.find(weatherId)).willReturn(weatherSummary);
    given(feedRepository.save(any(Feed.class))).willAnswer(invocation -> invocation.getArgument(0));
    given(feedClothesRepository.findByFeedAndClothesDeletedAtIsNull(any(Feed.class)))
        .willReturn(List.of());
    given(clothesAttributeRepository.findByClothesIn(anyList())).willReturn(List.of());
    given(attributeSelectableValueRepository
        .findByDefinitionInAndDeletedAtIsNullOrderByDisplayOrderAsc(anyList()))
        .willReturn(List.of());
    given(feedMapper.toDto(any(), any(), anyList(), anyMap(), anyMap(), anyBoolean()))
        .willReturn(feedDto);
    given(followRepository.findFollowerIdsByFolloweeId(authorId))
        .willReturn(List.of());

    FeedCreateRequest request = new FeedCreateRequest(
        authorId,
        weatherId,
        List.of(clothesId),
        "오늘의 피드"
    );

    FeedDto result = feedCommandService.createFeed(request, authorId);

    assertThat(result).isEqualTo(feedDto);
    verify(feedRepository).save(any(Feed.class));
  }

  @Test
  @DisplayName("피드 수정 성공")
  void updateFeed_success() {
    UUID authorId = UUID.randomUUID();
    UUID feedId = UUID.randomUUID();

    User author = User.create("author@test.com", "author", "password");
    ReflectionTestUtils.setField(author, "id", authorId);

    Feed feed = Feed.create(
        author,
        mock(Weather.class),
        objectMapper.createObjectNode(),
        "수정 전"
    );

    FeedDto feedDto = mock(FeedDto.class);

    given(feedRepository.findByIdAndDeletedAtIsNull(feedId)).willReturn(Optional.of(feed));
    given(feedLikeRepository.existsByFeedIdAndUserId(feedId, authorId)).willReturn(false);
    given(feedClothesRepository.findByFeedAndClothesDeletedAtIsNull(feed)).willReturn(List.of());
    given(clothesAttributeRepository.findByClothesIn(anyList())).willReturn(List.of());
    given(attributeSelectableValueRepository
        .findByDefinitionInAndDeletedAtIsNullOrderByDisplayOrderAsc(anyList()))
        .willReturn(List.of());
    given(feedMapper.toDto(any(), any(), anyList(), anyMap(), anyMap(), anyBoolean()))
        .willReturn(feedDto);

    FeedDto result = feedCommandService.updateFeed(
        feedId,
        new FeedUpdateRequest("수정 후"),
        authorId
    );

    assertThat(result).isEqualTo(feedDto);
    assertThat(feed.getContent()).isEqualTo("수정 후");
  }

  @Test
  @DisplayName("피드 삭제 성공")
  void deleteFeed_success() {
    UUID authorId = UUID.randomUUID();
    UUID feedId = UUID.randomUUID();

    User author = User.create("author@test.com", "author", "password");
    ReflectionTestUtils.setField(author, "id", authorId);

    Feed feed = Feed.create(
        author,
        mock(Weather.class),
        objectMapper.createObjectNode(),
        "삭제할 피드"
    );

    given(feedRepository.findByIdAndDeletedAtIsNull(feedId)).willReturn(Optional.of(feed));

    feedCommandService.deleteFeed(feedId, authorId);

    assertThat(feed.getDeletedAt()).isNotNull();
  }
  @Test
  @DisplayName("피드 생성 실패 - 요청 작성자와 인증 사용자가 다름")
  void createFeed_forbidden_authorMismatch() {
    UUID authorId = UUID.randomUUID();
    UUID currentUserId = UUID.randomUUID();

    FeedCreateRequest request = new FeedCreateRequest(
        authorId,
        UUID.randomUUID(),
        List.of(UUID.randomUUID()),
        "피드 내용"
    );

    assertThatThrownBy(() -> feedCommandService.createFeed(request, currentUserId))
        .isInstanceOf(FeedForbiddenException.class);

    verify(userRepository, never()).findById(authorId);
    verify(feedRepository, never()).save(any(Feed.class));
  }

  @Test
  @DisplayName("피드 생성 실패 - 요청한 옷 중 존재하지 않거나 삭제된 옷이 있음")
  void createFeed_clothesNotFound() {
    UUID authorId = UUID.randomUUID();
    UUID weatherId = UUID.randomUUID();
    UUID clothesId = UUID.randomUUID();

    User author = User.create("author@test.com", "author", "password");
    ReflectionTestUtils.setField(author, "id", authorId);

    Weather weather = mock(Weather.class);

    FeedCreateRequest request = new FeedCreateRequest(
        authorId,
        weatherId,
        List.of(clothesId),
        "피드 내용"
    );

    given(userRepository.findById(authorId)).willReturn(Optional.of(author));
    given(weatherRepository.findById(weatherId)).willReturn(Optional.of(weather));
    given(clothesRepository.findByIdInAndDeletedAtIsNull(List.of(clothesId)))
        .willReturn(List.of());

    assertThatThrownBy(() -> feedCommandService.createFeed(request, authorId))
        .isInstanceOf(FeedClothesNotFoundException.class);

    verify(feedRepository, never()).save(any(Feed.class));
  }

  @Test
  @DisplayName("피드 생성 실패 - 본인 소유가 아닌 옷이 포함됨")
  void createFeed_notOwnedClothes() {
    UUID authorId = UUID.randomUUID();
    UUID otherUserId = UUID.randomUUID();
    UUID weatherId = UUID.randomUUID();
    UUID clothesId = UUID.randomUUID();

    User author = User.create("author@test.com", "author", "password");
    ReflectionTestUtils.setField(author, "id", authorId);

    User otherUser = User.create("other@test.com", "other", "password");
    ReflectionTestUtils.setField(otherUser, "id", otherUserId);

    Weather weather = mock(Weather.class);
    Clothes clothes = mock(Clothes.class);

    given(clothes.getOwner()).willReturn(otherUser);

    FeedCreateRequest request = new FeedCreateRequest(
        authorId,
        weatherId,
        List.of(clothesId),
        "피드 내용"
    );

    given(userRepository.findById(authorId)).willReturn(Optional.of(author));
    given(weatherRepository.findById(weatherId)).willReturn(Optional.of(weather));
    given(clothesRepository.findByIdInAndDeletedAtIsNull(List.of(clothesId)))
        .willReturn(List.of(clothes));

    assertThatThrownBy(() -> feedCommandService.createFeed(request, authorId))
        .isInstanceOf(FeedForbiddenException.class);

    verify(feedRepository, never()).save(any(Feed.class));
  }

  @Test
  @DisplayName("피드 수정 실패 - 작성자가 아님")
  void updateFeed_forbidden() {
    UUID feedId = UUID.randomUUID();
    UUID authorId = UUID.randomUUID();
    UUID currentUserId = UUID.randomUUID();

    User author = User.create("author@test.com", "author", "password");
    ReflectionTestUtils.setField(author, "id", authorId);

    Feed feed = Feed.create(
        author,
        mock(Weather.class),
        objectMapper.createObjectNode(),
        "수정 전"
    );

    given(feedRepository.findByIdAndDeletedAtIsNull(feedId)).willReturn(Optional.of(feed));

    assertThatThrownBy(() -> feedCommandService.updateFeed(
        feedId,
        new FeedUpdateRequest("수정 내용"),
        currentUserId
    )).isInstanceOf(FeedForbiddenException.class);

    verify(feedMapper, never()).toDto(any(), any(), anyList(), anyMap(), anyMap(), anyBoolean());
  }

  @Test
  @DisplayName("피드 삭제 실패 - 작성자가 아님")
  void deleteFeed_forbidden() {
    UUID feedId = UUID.randomUUID();
    UUID authorId = UUID.randomUUID();
    UUID currentUserId = UUID.randomUUID();

    User author = User.create("author@test.com", "author", "password");
    ReflectionTestUtils.setField(author, "id", authorId);

    Feed feed = Feed.create(
        author,
        mock(Weather.class),
        objectMapper.createObjectNode(),
        "삭제 대상 피드"
    );

    given(feedRepository.findByIdAndDeletedAtIsNull(feedId)).willReturn(Optional.of(feed));

    assertThatThrownBy(() -> feedCommandService.deleteFeed(feedId, currentUserId))
        .isInstanceOf(FeedForbiddenException.class);

    assertThat(feed.getDeletedAt()).isNull();
  }
}
