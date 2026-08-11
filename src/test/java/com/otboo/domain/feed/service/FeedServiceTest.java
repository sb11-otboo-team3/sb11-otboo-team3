package com.otboo.domain.feed.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.eq;

import com.otboo.domain.feed.core.dto.request.SortBy;
import com.otboo.domain.feed.core.dto.request.SortDirection;
import com.otboo.domain.feed.comment.dto.response.FeedCommentDtoCursorResponse;
import com.otboo.domain.feed.core.dto.response.FeedDtoCursorResponse;
import com.otboo.domain.weather.dto.PrecipitationDto;
import com.otboo.domain.weather.dto.TemperatureDto;
import com.otboo.domain.weather.entity.PrecipitationType;
import com.otboo.domain.weather.entity.SkyStatus;
import java.time.Instant;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.otboo.domain.clothes.entity.Clothes;
import com.otboo.domain.clothes.repository.AttributeSelectableValueRepository;
import com.otboo.domain.clothes.repository.ClothesAttributeRepository;
import com.otboo.domain.clothes.repository.ClothesRepository;
import com.otboo.domain.feed.comment.dto.request.FeedCommentCreateRequest;
import com.otboo.domain.feed.core.dto.request.FeedCreateRequest;
import com.otboo.domain.feed.core.dto.request.FeedUpdateRequest;
import com.otboo.domain.feed.comment.dto.response.FeedCommentDto;
import com.otboo.domain.feed.core.dto.response.FeedDto;
import com.otboo.domain.feed.comment.entity.Comment;
import com.otboo.domain.feed.core.entity.Feed;
import com.otboo.domain.feed.like.entity.FeedLike;
import com.otboo.domain.feed.comment.mapper.FeedCommentMapper;
import com.otboo.domain.feed.core.mapper.FeedMapper;
import com.otboo.domain.feed.clothes.repository.FeedClothesRepository;
import com.otboo.domain.feed.comment.repository.FeedCommentRepository;
import com.otboo.domain.feed.like.repository.FeedLikeRepository;
import com.otboo.domain.feed.core.repository.FeedRepository;
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
  private FeedCommentRepository feedCommentRepository;

  @Mock
  private FeedCommentMapper feedCommentMapper;

  @Mock
  private FeedMapper feedMapper;

  @Spy
  private ObjectMapper objectMapper = new ObjectMapper();

  @InjectMocks
  private FeedService feedService;

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

    FeedCreateRequest request = new FeedCreateRequest(
        authorId,
        weatherId,
        List.of(clothesId),
        "오늘의 피드"
    );

    FeedDto result = feedService.createFeed(request, authorId);

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
    given(feedClothesRepository.findByFeedAndClothesDeletedAtIsNull(feed)).willReturn(List.of());
    given(clothesAttributeRepository.findByClothesIn(anyList())).willReturn(List.of());
    given(attributeSelectableValueRepository
        .findByDefinitionInAndDeletedAtIsNullOrderByDisplayOrderAsc(anyList()))
        .willReturn(List.of());
    given(feedMapper.toDto(any(), any(), anyList(), anyMap(), anyMap(), anyBoolean()))
        .willReturn(feedDto);

    FeedDto result = feedService.updateFeed(feedId, new FeedUpdateRequest("수정 후"), authorId);

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

    feedService.deleteFeed(feedId, authorId);

    assertThat(feed.getDeletedAt()).isNotNull();
  }

  @Test
  @DisplayName("피드 좋아요 생성 성공 테스트")
  void createFeedLike_success() {
    UUID feedId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();

    User user = User.create("user@test.com", "user", "password");
    ReflectionTestUtils.setField(user, "id", userId);

    Feed feed = Feed.create(
        user,
        mock(Weather.class),
        objectMapper.createObjectNode(),
        "좋아요 대상 피드"
    );

    given(userRepository.findById(userId)).willReturn(Optional.of(user));
    given(feedRepository.findByIdAndDeletedAtIsNull(feedId)).willReturn(Optional.of(feed));
    given(feedLikeRepository.existsByFeedIdAndUserId(feedId, userId)).willReturn(false);

    feedService.createFeedLike(feedId, userId);

    verify(feedLikeRepository).save(any(FeedLike.class));
    verify(feedRepository).increaseLikeCount(feedId);
  }

  @Test
  @DisplayName("피드 좋아요 취소 성공 테스트")
  void deleteFeedLike_success() {
    UUID feedId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();

    User user = User.create("user@test.com", "user", "password");
    ReflectionTestUtils.setField(user, "id", userId);

    Feed feed = Feed.create(
        user,
        mock(Weather.class),
        objectMapper.createObjectNode(),
        "좋아요 취소 대상 피드"
    );
    FeedLike feedLike = FeedLike.create(feed, user);

    given(userRepository.findById(userId)).willReturn(Optional.of(user));
    given(feedRepository.findByIdAndDeletedAtIsNull(feedId)).willReturn(Optional.of(feed));
    given(feedLikeRepository.findByFeedIdAndUserId(feedId, userId))
        .willReturn(Optional.of(feedLike));

    feedService.deleteFeedLike(feedId, userId);

    verify(feedLikeRepository).delete(feedLike);
    verify(feedRepository).decreaseLikeCount(feedId);
  }

  @Test
  @DisplayName("피드 댓글 생성 성공 테스트")
  void createFeedComment_success() {
    UUID feedId = UUID.randomUUID();
    UUID authorId = UUID.randomUUID();

    User author = User.create("author@test.com", "author", "password");
    ReflectionTestUtils.setField(author, "id", authorId);

    Feed feed = Feed.create(
        author,
        mock(Weather.class),
        objectMapper.createObjectNode(),
        "댓글 대상 피드"
    );

    FeedCommentDto feedCommentDto = mock(FeedCommentDto.class);

    FeedCommentCreateRequest request = new FeedCommentCreateRequest(
        feedId,
        authorId,
        "댓글 내용"
    );

    given(userRepository.findById(authorId)).willReturn(Optional.of(author));
    given(feedRepository.findByIdAndDeletedAtIsNull(feedId)).willReturn(Optional.of(feed));
    given(feedCommentRepository.save(any(Comment.class)))
        .willAnswer(invocation -> invocation.getArgument(0));
    given(feedCommentMapper.toDto(any(Comment.class))).willReturn(feedCommentDto);

    FeedCommentDto result = feedService.createFeedComment(feedId, request, authorId);

    assertThat(result).isEqualTo(feedCommentDto);
    verify(feedCommentRepository).save(any(Comment.class));
    verify(feedRepository).increaseCommentCount(feedId);
  }

  @Test
  @DisplayName("피드 목록 조회 성공")
  void getFeeds_success() {
    UUID currentUserId = UUID.randomUUID();

    User author = User.create("author@test.com", "author", "password");
    ReflectionTestUtils.setField(author, "id", currentUserId);

    WeatherSummaryDto weatherSummary = new WeatherSummaryDto(
        UUID.randomUUID(),
        SkyStatus.CLEAR,
        new PrecipitationDto(PrecipitationType.NONE, 0.0, 0.0),
        new TemperatureDto(20.0, 0.0, 18.0, 25.0)
    );

    Feed feed1 = Feed.create(
        author,
        mock(Weather.class),
        objectMapper.valueToTree(weatherSummary),
        "첫 번째 피드"
    );
    UUID feed1Id = UUID.randomUUID();
    Instant feed1CreatedAt = Instant.parse("2026-08-10T09:00:00Z");
    ReflectionTestUtils.setField(feed1, "id", feed1Id);
    ReflectionTestUtils.setField(feed1, "createdAt", feed1CreatedAt);

    Feed feed2 = Feed.create(
        author,
        mock(Weather.class),
        objectMapper.valueToTree(weatherSummary),
        "두 번째 피드"
    );
    ReflectionTestUtils.setField(feed2, "id", UUID.randomUUID());
    ReflectionTestUtils.setField(feed2, "createdAt", Instant.parse("2026-08-10T08:00:00Z"));

    FeedDto feedDto = mock(FeedDto.class);

    given(feedRepository.findFeeds(
        null,
        null,
        2,
        SortBy.createdAt,
        SortDirection.DESCENDING,
        null,
        null,
        null,
        null
    )).willReturn(List.of(feed1, feed2));

    given(feedLikeRepository.findByFeedIdInAndUserId(anyList(), eq(currentUserId)))
        .willReturn(List.of());
    given(feedClothesRepository.findByFeedInAndClothesDeletedAtIsNull(anyList()))
        .willReturn(List.of());
    given(clothesAttributeRepository.findByClothesIn(anyList()))
        .willReturn(List.of());
    given(attributeSelectableValueRepository
        .findByDefinitionInAndDeletedAtIsNullOrderByDisplayOrderAsc(anyList()))
        .willReturn(List.of());
    given(feedMapper.toDto(any(), any(), anyList(), anyMap(), anyMap(), anyBoolean()))
        .willReturn(feedDto);
    given(feedRepository.countFeeds(null, null, null, null))
        .willReturn(2L);

    FeedDtoCursorResponse result = feedService.getFeeds(
        null,
        null,
        1,
        SortBy.createdAt,
        SortDirection.DESCENDING,
        null,
        null,
        null,
        null,
        currentUserId
    );

    assertThat(result.data()).containsExactly(feedDto);
    assertThat(result.hasNext()).isTrue();
    assertThat(result.nextCursor()).isEqualTo(feed1CreatedAt.toString());
    assertThat(result.nextIdAfter()).isEqualTo(feed1Id);
    assertThat(result.totalCount()).isEqualTo(2L);
    assertThat(result.sortBy()).isEqualTo("createdAt");
    assertThat(result.sortDirection()).isEqualTo("DESCENDING");

    verify(feedRepository).findFeeds(
        null,
        null,
        2,
        SortBy.createdAt,
        SortDirection.DESCENDING,
        null,
        null,
        null,
        null
    );
  }

  @Test
  @DisplayName("피드 댓글 목록 조회 성공 테스트")
  void getComment_success() {
    UUID feedId = UUID.randomUUID();
    UUID authorId = UUID.randomUUID();

    User author = User.create("author@test.com", "author", "password");
    ReflectionTestUtils.setField(author, "id", authorId);

    Feed feed = Feed.create(
        author,
        mock(Weather.class),
        objectMapper.valueToTree(mock(WeatherSummaryDto.class)),
        "피드 내용"
    );
    ReflectionTestUtils.setField(feed, "id", feedId);

    Comment comment1 = Comment.create(feed, author, "첫 번째 댓글");
    UUID comment1Id = UUID.randomUUID();
    Instant comment1CreatedAt = Instant.parse("2026-08-11T00:00:00Z");
    ReflectionTestUtils.setField(comment1, "id", comment1Id);
    ReflectionTestUtils.setField(comment1, "createdAt", comment1CreatedAt);

    Comment comment2 = Comment.create(feed, author, "두 번째 댓글");
    ReflectionTestUtils.setField(comment2, "id", UUID.randomUUID());
    ReflectionTestUtils.setField(comment2, "createdAt", Instant.parse("2026-08-11T01:00:00Z"));

    FeedCommentDto feedCommentDto = mock(FeedCommentDto.class);

    given(feedRepository.findByIdAndDeletedAtIsNull(feedId))
        .willReturn(Optional.of(feed));
    given(feedCommentRepository.findComments(feedId, null, null, 2))
        .willReturn(List.of(comment1, comment2));
    given(feedCommentMapper.toDto(comment1))
        .willReturn(feedCommentDto);
    given(feedCommentRepository.countComments(feedId))
        .willReturn(2L);

    FeedCommentDtoCursorResponse result = feedService.getComment(
        feedId,
        null,
        null,
        1
    );

    assertThat(result.data()).containsExactly(feedCommentDto);
    assertThat(result.hasNext()).isTrue();
    assertThat(result.nextCursor()).isEqualTo(comment1CreatedAt.toString());
    assertThat(result.nextIdAfter()).isEqualTo(comment1Id);
    assertThat(result.totalCount()).isEqualTo(2L);
    assertThat(result.sortBy()).isEqualTo("createdAt");
    assertThat(result.sortDirection()).isEqualTo("ASCENDING");

    verify(feedRepository).findByIdAndDeletedAtIsNull(feedId);
    verify(feedCommentRepository).findComments(feedId, null, null, 2);
    verify(feedCommentMapper).toDto(comment1);
    verify(feedCommentRepository).countComments(feedId);
  }
}
