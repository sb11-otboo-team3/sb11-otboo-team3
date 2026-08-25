package com.otboo.domain.feed.like.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import com.otboo.domain.feed.core.cache.FeedAuthorListCache;
import com.otboo.domain.feed.core.search.FeedSearchService;
import com.otboo.domain.notification.event.NotificationEvent;
import org.springframework.context.ApplicationEventPublisher;
import com.otboo.domain.feed.core.exception.FeedNotFoundException;
import com.otboo.domain.feed.core.exception.FeedUserNotFoundException;
import com.otboo.domain.feed.like.exception.DuplicateFeedLikeException;
import com.otboo.domain.feed.like.exception.FeedLikeNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.otboo.domain.feed.core.entity.Feed;
import com.otboo.domain.feed.core.repository.FeedRepository;
import com.otboo.domain.feed.like.entity.FeedLike;
import com.otboo.domain.feed.like.repository.FeedLikeRepository;
import com.otboo.domain.user.entity.User;
import com.otboo.domain.user.repository.UserRepository;
import com.otboo.domain.weather.entity.Weather;
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
class FeedLikeServiceTest {

  @Mock
  private UserRepository userRepository;

  @Mock
  private FeedRepository feedRepository;

  @Mock
  private FeedLikeRepository feedLikeRepository;

  @Mock
  private ApplicationEventPublisher eventPublisher;

  @Mock
  private FeedAuthorListCache feedAuthorListCache;

  @Mock
  private FeedSearchService feedSearchService;

  @Spy
  private ObjectMapper objectMapper = new ObjectMapper();

  @InjectMocks
  private FeedLikeService feedLikeService;

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

    feedLikeService.createFeedLike(feedId, userId);

    verify(feedLikeRepository).saveAndFlush(any(FeedLike.class));
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

    given(userRepository.findById(userId)).willReturn(Optional.of(user));
    given(feedRepository.findByIdAndDeletedAtIsNull(feedId)).willReturn(Optional.of(feed));
    given(feedLikeRepository.deleteByFeedIdAndUserId(feedId, userId))
        .willReturn(1L);

    feedLikeService.deleteFeedLike(feedId, userId);

    verify(feedLikeRepository).deleteByFeedIdAndUserId(feedId, userId);
    verify(feedRepository).decreaseLikeCount(feedId);
  }

  @Test
  @DisplayName("피드 좋아요 생성 실패 - 이미 좋아요를 누른 피드")
  void createFeedLike_duplicate() {
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
    given(feedLikeRepository.saveAndFlush(any(FeedLike.class)))
        .willThrow(DataIntegrityViolationException.class);

    assertThatThrownBy(() -> feedLikeService.createFeedLike(feedId, userId))
        .isInstanceOf(DuplicateFeedLikeException.class);

    verify(feedRepository, never()).increaseLikeCount(feedId);
  }

  @Test
  @DisplayName("피드 좋아요 취소 실패 - 좋아요를 찾을 수 없음")
  void deleteFeedLike_notFound() {
    UUID feedId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();

    User user = User.create("user@test.com", "user", "password");

    Feed feed = Feed.create(
        user,
        mock(Weather.class),
        objectMapper.createObjectNode(),
        "좋아요 취소 대상 피드"
    );

    given(userRepository.findById(userId)).willReturn(Optional.of(user));
    given(feedRepository.findByIdAndDeletedAtIsNull(feedId)).willReturn(Optional.of(feed));
    given(feedLikeRepository.deleteByFeedIdAndUserId(feedId, userId))
        .willReturn(0L);

    assertThatThrownBy(() -> feedLikeService.deleteFeedLike(feedId, userId))
        .isInstanceOf(FeedLikeNotFoundException.class);

    verify(feedRepository, never()).decreaseLikeCount(feedId);
  }

  @Test
  @DisplayName("다른 사용자의 피드에 좋아요를 누르면 알림 이벤트를 발행한다")
  void createFeedLike_publishNotification_whenOtherUserFeed() {
    UUID feedId = UUID.randomUUID();
    UUID currentUserId = UUID.randomUUID();
    UUID feedAuthorId = UUID.randomUUID();

    User currentUser = User.create("current@test.com", "current", "password");
    ReflectionTestUtils.setField(currentUser, "id", currentUserId);

    User feedAuthor = User.create("author@test.com", "author", "password");
    ReflectionTestUtils.setField(feedAuthor, "id", feedAuthorId);

    Feed feed = Feed.create(
        feedAuthor,
        mock(Weather.class),
        objectMapper.createObjectNode(),
        "다른 사람 피드"
    );

    given(userRepository.findById(currentUserId)).willReturn(Optional.of(currentUser));
    given(feedRepository.findByIdAndDeletedAtIsNull(feedId)).willReturn(Optional.of(feed));

    feedLikeService.createFeedLike(feedId, currentUserId);

    verify(feedLikeRepository).saveAndFlush(any(FeedLike.class));
    verify(feedRepository).increaseLikeCount(feedId);
    verify(eventPublisher, times(1)).publishEvent(any(NotificationEvent.class));
  }

  @Test
  @DisplayName("피드 좋아요 생성 실패 - 사용자를 찾을 수 없음")
  void createFeedLike_userNotFound() {
    UUID feedId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();

    given(userRepository.findById(userId)).willReturn(Optional.empty());

    assertThatThrownBy(() -> feedLikeService.createFeedLike(feedId, userId))
        .isInstanceOf(FeedUserNotFoundException.class);

    verify(feedRepository, never()).findByIdAndDeletedAtIsNull(feedId);
    verify(feedLikeRepository, never()).saveAndFlush(any());
  }

  @Test
  @DisplayName("피드 좋아요 생성 실패 - 피드를 찾을 수 없음")
  void createFeedLike_feedNotFound() {
    UUID feedId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();

    User user = User.create("user-not-found-feed@test.com", "user", "password");

    given(userRepository.findById(userId)).willReturn(Optional.of(user));
    given(feedRepository.findByIdAndDeletedAtIsNull(feedId)).willReturn(Optional.empty());

    assertThatThrownBy(() -> feedLikeService.createFeedLike(feedId, userId))
        .isInstanceOf(FeedNotFoundException.class);

    verify(feedLikeRepository, never()).saveAndFlush(any());
    verify(feedRepository, never()).increaseLikeCount(feedId);
  }
}
