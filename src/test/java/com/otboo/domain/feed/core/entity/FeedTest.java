package com.otboo.domain.feed.core.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.JsonNode;
import com.otboo.domain.user.entity.User;
import com.otboo.domain.weather.entity.Weather;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FeedTest {

  @Test
  @DisplayName("피드 생성 성공")
  void create_success() {
    User author = User.create("author@test.com", "author", "password");
    Weather weather = mock(Weather.class);
    JsonNode weatherSnapshot = mock(JsonNode.class);

    Feed feed = Feed.create(author, weather, weatherSnapshot, "피드 내용");

    assertThat(feed.getAuthor()).isEqualTo(author);
    assertThat(feed.getWeather()).isEqualTo(weather);
    assertThat(feed.getWeatherSnapshot()).isEqualTo(weatherSnapshot);
    assertThat(feed.getContent()).isEqualTo("피드 내용");
    assertThat(feed.getLikeCount()).isZero();
    assertThat(feed.getCommentCount()).isZero();
  }

  @Test
  @DisplayName("피드 내용 수정 성공")
  void updateContent_success() {
    Feed feed = Feed.create(
        User.create("author@test.com", "author", "password"),
        mock(Weather.class),
        mock(JsonNode.class),
        "수정 전"
    );

    feed.updateContent("수정 후");

    assertThat(feed.getContent()).isEqualTo("수정 후");
  }
}