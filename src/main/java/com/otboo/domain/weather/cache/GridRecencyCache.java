package com.otboo.domain.weather.cache;

import com.otboo.domain.weather.util.WeatherGrid;

public interface GridRecencyCache {

  boolean isRecentlyConfirmed(WeatherGrid grid);

  void markConfirmed(WeatherGrid grid);
}