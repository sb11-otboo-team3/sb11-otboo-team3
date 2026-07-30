package com.otboo.domain.weather.service;

import com.otboo.domain.weather.dto.WeatherAPILocation;

public interface WeatherService {

  WeatherAPILocation getLocation(double latitude, double longitude);
}