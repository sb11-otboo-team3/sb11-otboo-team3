package com.otboo.domain.weather.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import com.otboo.domain.weather.util.WeatherGrid;
import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public class CaffeineGridRecencyCache implements GridRecencyCache {

  private static final long MAXIMUM_SIZE = 1000;
  private static final Duration EXPIRE_AFTER_WRITE = Duration.ofMinutes(10);

  private final Cache<WeatherGrid, Boolean> cache;

  public CaffeineGridRecencyCache() {
    this(Ticker.systemTicker());
  }

  // 테스트용 생성자.
  CaffeineGridRecencyCache(Ticker ticker) {
    this.cache = Caffeine.newBuilder()
        .maximumSize(MAXIMUM_SIZE)
        .expireAfterWrite(EXPIRE_AFTER_WRITE)
        .ticker(ticker)
        .build();
  }

  @Override
  public boolean isRecentlyConfirmed(WeatherGrid grid) {
    return cache.getIfPresent(grid) != null;
  }

  @Override
  public void markConfirmed(WeatherGrid grid) {
    cache.put(grid, Boolean.TRUE);
  }
}