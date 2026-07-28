package com.otboo.domain.weather.cache;

import com.otboo.domain.weather.client.KakaoRegion;
import com.otboo.domain.weather.util.WeatherGrid;
import java.util.Optional;

public interface LocationRegionCache {

  Optional<KakaoRegion> get(WeatherGrid grid);

  void put(WeatherGrid grid, KakaoRegion region);
}