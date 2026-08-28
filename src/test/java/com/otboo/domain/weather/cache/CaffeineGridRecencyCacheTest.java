package com.otboo.domain.weather.cache;

import static org.assertj.core.api.Assertions.assertThat;

import com.otboo.domain.weather.util.WeatherGrid;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CaffeineGridRecencyCacheTest {

  // 진짜 10분을 기다릴 수는 없으니, 시간 자체를 손으로 흘려보낼 수 있는 가짜 시계를 주입해서 TTL을 검증한다.
  private final AtomicLong currentNanos = new AtomicLong(0);
  private final CaffeineGridRecencyCache cache = new CaffeineGridRecencyCache(currentNanos::get);

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

  @Test
  @DisplayName("10분이 지나기 전이면 여전히 최근에 확인된 것으로 본다")
  void stillRecentlyConfirmedJustBeforeTtlExpires() {
    // given
    WeatherGrid grid = new WeatherGrid(60, 127);
    cache.markConfirmed(grid);

    // when - 10분 - 1초 경과
    currentNanos.addAndGet(Duration.ofMinutes(10).minusSeconds(1).toNanos());

    // then
    assertThat(cache.isRecentlyConfirmed(grid)).isTrue();
  }

  @Test
  @DisplayName("10분이 지나면 다시 최근에 확인되지 않은 것으로 본다")
  void notRecentlyConfirmedAfterTtlExpires() {
    // given
    WeatherGrid grid = new WeatherGrid(60, 127);
    cache.markConfirmed(grid);

    // when - 10분 + 1초 경과
    currentNanos.addAndGet(Duration.ofMinutes(10).plusSeconds(1).toNanos());

    // then
    assertThat(cache.isRecentlyConfirmed(grid)).isFalse();
  }
}
