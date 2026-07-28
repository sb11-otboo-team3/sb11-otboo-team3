package com.otboo.domain.weather.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.otboo.domain.weather.client.KakaoRegion;
import com.otboo.domain.weather.util.WeatherGrid;
import java.time.Duration;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class CaffeineLocationRegionCache implements LocationRegionCache {

  private static final long MAXIMUM_SIZE = 1000;
  private static final Duration EXPIRE_AFTER_WRITE = Duration.ofHours(1);

  private final Cache<WeatherGrid, KakaoRegion> cache = Caffeine.newBuilder()
      .maximumSize(MAXIMUM_SIZE)
      .expireAfterWrite(EXPIRE_AFTER_WRITE)
      .build();

  @Override
  public Optional<KakaoRegion> get(WeatherGrid grid) {
    return Optional.ofNullable(cache.getIfPresent(grid));
  }

  @Override
  public void put(WeatherGrid grid, KakaoRegion region) {
    cache.put(grid, region);
  }
}