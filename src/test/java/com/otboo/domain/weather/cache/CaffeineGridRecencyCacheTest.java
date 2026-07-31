package com.otboo.domain.weather.cache;

import static org.assertj.core.api.Assertions.assertThat;

import com.otboo.domain.weather.util.WeatherGrid;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CaffeineGridRecencyCacheTest {

  private final CaffeineGridRecencyCache cache = new CaffeineGridRecencyCache();

  @Test
  @DisplayName("확인한 적 없는 격자는 최근에 확인되지 않은 것으로 본다")
  void notRecentlyConfirmedWhenNeverMarked() {
    // given
    WeatherGrid grid = new WeatherGrid(60, 127);

    // when & then
    assertThat(cache.isRecentlyConfirmed(grid)).isFalse();
  }

  @Test
  @DisplayName("확인 표시를 하면 그 이후엔 최근에 확인된 것으로 본다")
  void recentlyConfirmedAfterMarking() {
    // given
    WeatherGrid grid = new WeatherGrid(60, 127);

    // when
    cache.markConfirmed(grid);

    // then
    assertThat(cache.isRecentlyConfirmed(grid)).isTrue();
  }
}