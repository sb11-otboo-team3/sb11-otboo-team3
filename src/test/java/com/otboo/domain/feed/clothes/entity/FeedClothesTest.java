package com.otboo.domain.feed.clothes.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.otboo.domain.clothes.entity.Clothes;
import com.otboo.domain.feed.core.entity.Feed;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FeedClothesTest {

  @Test
  @DisplayName("피드 의상 연결 생성 성공")
  void create_success() {
    Feed feed = mock(Feed.class);
    Clothes clothes = mock(Clothes.class);

    FeedClothes feedClothes = FeedClothes.create(feed, clothes);

    assertThat(feedClothes.getFeed()).isEqualTo(feed);
    assertThat(feedClothes.getClothes()).isEqualTo(clothes);
  }
}