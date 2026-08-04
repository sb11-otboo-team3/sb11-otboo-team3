package com.otboo.domain.weather.service;

import com.otboo.domain.weather.dto.WeatherAPILocation;
import com.otboo.domain.weather.dto.WeatherDto;
import com.otboo.domain.weather.dto.WeatherSummaryDto;
import java.util.List;
import java.util.UUID;

public interface WeatherService {

  WeatherAPILocation getLocation(double latitude, double longitude);

  List<WeatherDto> getWeathers(double latitude, double longitude);

  WeatherSummaryDto getWeatherSummary(UUID weatherId);
}