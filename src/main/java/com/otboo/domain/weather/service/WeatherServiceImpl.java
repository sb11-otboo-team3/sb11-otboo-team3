package com.otboo.domain.weather.service;

import com.otboo.domain.weather.dto.WeatherAPILocation;
import org.springframework.stereotype.Service;

@Service
public class WeatherServiceImpl implements WeatherService {

  @Override
  public WeatherAPILocation getLocation(double latitude, double longitude) {
    throw new UnsupportedOperationException("아직 구현되지 않았습니다.");
  }
}