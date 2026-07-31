package com.otboo.domain.weather.controller;

import com.otboo.domain.weather.dto.WeatherAPILocation;
import com.otboo.domain.weather.service.WeatherService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class WeatherControllerImpl implements WeatherController {

  private final WeatherService weatherService;

  @Override
  public WeatherAPILocation getLocation(double longitude, double latitude) {
    return weatherService.getLocation(latitude, longitude);
  }
}