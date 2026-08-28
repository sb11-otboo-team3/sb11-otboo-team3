package com.otboo.domain.feed.like.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.otboo.domain.feed.core.entity.Feed;
import com.otboo.domain.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FeedLikeTest {

  @Test
  @DisplayName("피드 좋아요 생성 성공")
  void create_success() {
    Feed feed = mock(Feed.class);
    User user = User.create("user@test.com", "user", "password");

    FeedLike feedLike = FeedLike.create(feed, user);

    assertThat(feedLike.getFeed()).isEqualTo(feed);
    assertThat(feedLike.getUser()).isEqualTo(user);
  }
}