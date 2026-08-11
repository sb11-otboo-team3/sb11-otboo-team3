package com.otboo.domain.feed.like.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

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
    given(feedLikeRepository.existsByFeedIdAndUserId(feedId, userId)).willReturn(false);

    feedLikeService.createFeedLike(feedId, userId);

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

    feedLikeService.deleteFeedLike(feedId, userId);

    verify(feedLikeRepository).delete(feedLike);
    verify(feedRepository).decreaseLikeCount(feedId);
  }
}
