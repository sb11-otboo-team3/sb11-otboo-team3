package com.otboo.domain.weather.cache;

import static org.assertj.core.api.Assertions.assertThat;

import com.otboo.domain.weather.client.KakaoRegion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CaffeineLocationRecencyCacheTest {

  private final CaffeineLocationRecencyCache cache = new CaffeineLocationRecencyCache();

  @Test
  @DisplayName("확인한 적 없는 행정구역은 최근에 확인되지 않은 것으로 본다")
  void notRecentlyConfirmedWhenNeverMarked() {
    // given
    KakaoRegion region = new KakaoRegion("서울특별시", "강서구", "마곡동");

    // when & then
    assertThat(cache.isRecentlyConfirmed(region)).isFalse();
  }

  @Test
  @DisplayName("확인 표시를 하면 그 이후엔 최근에 확인된 것으로 본다")
  void recentlyConfirmedAfterMarking() {
    // given
    KakaoRegion region = new KakaoRegion("서울특별시", "강서구", "마곡동");

    // when
    cache.markConfirmed(region);

    // then
    assertThat(cache.isRecentlyConfirmed(region)).isTrue();
  }
}