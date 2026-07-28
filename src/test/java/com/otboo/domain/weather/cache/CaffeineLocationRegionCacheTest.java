package com.otboo.domain.weather.cache;

import static org.assertj.core.api.Assertions.assertThat;

import com.otboo.domain.weather.client.KakaoRegion;
import com.otboo.domain.weather.util.WeatherGrid;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CaffeineLocationRegionCacheTest {

  private final CaffeineLocationRegionCache cache = new CaffeineLocationRegionCache();

  @Test
  @DisplayName("저장한 적 없는 좌표를 조회하면 빈 Optional을 반환한다")
  void returnsEmptyWhenNotCached() {
    // when
    Optional<KakaoRegion> found = cache.get(new WeatherGrid(60, 127));

    // then
    assertThat(found).isEmpty();
  }

  @Test
  @DisplayName("저장한 좌표를 조회하면 저장했던 값을 반환한다")
  void returnsCachedValueAfterPut() {
    // given
    WeatherGrid grid = new WeatherGrid(60, 127);
    KakaoRegion region = new KakaoRegion("서울특별시", "강서구", "마곡동");

    // when
    cache.put(grid, region);

    // then
    assertThat(cache.get(grid)).contains(region);
  }
}
