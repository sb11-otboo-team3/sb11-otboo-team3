package com.otboo.domain.weather.cache;

import com.otboo.domain.weather.client.KakaoRegion;

public interface LocationRecencyCache {

  boolean isRecentlyConfirmed(KakaoRegion region);

  void markConfirmed(KakaoRegion region);
}