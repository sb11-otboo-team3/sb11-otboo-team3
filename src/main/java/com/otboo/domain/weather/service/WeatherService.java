package com.otboo.domain.weather.service;

import com.otboo.domain.weather.dto.WeatherAPILocation;
import com.otboo.domain.weather.dto.WeatherDto;
import java.util.List;

public interface WeatherService {

  WeatherAPILocation getLocation(double latitude, double longitude);

  List<WeatherDto> getWeathers(double latitude, double longitude);
}