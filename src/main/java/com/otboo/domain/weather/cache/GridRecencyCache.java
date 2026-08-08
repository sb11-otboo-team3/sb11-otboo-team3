package com.otboo.domain.weather.cache;

import com.otboo.domain.weather.util.WeatherGrid;

public interface GridRecencyCache {

  // 최근에 접근한적 있는지
  boolean isRecentlyConfirmed(WeatherGrid grid);

  // 최근에 접근함.
  void markConfirmed(WeatherGrid grid);
}