package com.otboo.domain.weather.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.otboo.domain.weather.client.KakaoRegion;
import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public class CaffeineLocationRecencyCache implements LocationRecencyCache {

  private static final long MAXIMUM_SIZE = 1000;
  private static final Duration EXPIRE_AFTER_WRITE = Duration.ofMinutes(10);

  private final Cache<KakaoRegion, Boolean> cache = Caffeine.newBuilder()
      .maximumSize(MAXIMUM_SIZE)
      .expireAfterWrite(EXPIRE_AFTER_WRITE)
      .build();

  //최근에 DB에서 접근했는지
  @Override
  public boolean isRecentlyConfirmed(KakaoRegion region) {
    return cache.getIfPresent(region) != null;
  }

  //DB에서 최근에 접근함
  @Override
  public void markConfirmed(KakaoRegion region) {
    cache.put(region, Boolean.TRUE);
  }
}


