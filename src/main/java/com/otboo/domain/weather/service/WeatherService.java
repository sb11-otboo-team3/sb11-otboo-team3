package com.otboo.domain.weather.service;

import com.otboo.domain.weather.dto.WeatherAPILocation;
import com.otboo.domain.weather.dto.WeatherDto;
import java.util.List;
import reactor.core.publisher.Mono;

public interface WeatherService {

  Mono<WeatherAPILocation> getLocation(double latitude, double longitude);

  Mono<List<WeatherDto>> getWeathers(double latitude, double longitude);
}