package com.otboo.domain.weather.controller;

import com.otboo.domain.weather.dto.WeatherAPILocation;
import com.otboo.domain.weather.dto.WeatherDto;
import com.otboo.domain.weather.service.WeatherService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
public class WeatherControllerImpl implements WeatherController {

  private final WeatherService weatherService;

  @Override
  //카카오 api를 통해 현재 위치의 행정구역을 가져오는 api
  public Mono<WeatherAPILocation> getLocation(double longitude, double latitude) {
    return weatherService.getLocation(latitude, longitude);
  }

  @Override
  //기상청 api를 통해 현재 위치의 날씨를 가져오는 api
  public Mono<List<WeatherDto>> getWeathers(double latitude, double longitude) {
    return weatherService.getWeathers(latitude, longitude);
  }
}