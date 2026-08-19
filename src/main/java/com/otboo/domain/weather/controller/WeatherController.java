package com.otboo.domain.weather.controller;

import com.otboo.domain.weather.dto.WeatherAPILocation;
import com.otboo.domain.weather.dto.WeatherDto;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import reactor.core.publisher.Mono;

@RequestMapping("/api/weathers")
public interface WeatherController {

  @GetMapping("/location")
  Mono<ResponseEntity<WeatherAPILocation>> getLocation(
      @RequestParam double longitude,
      @RequestParam double latitude
  );

  @GetMapping
  Mono<ResponseEntity<List<WeatherDto>>> getWeathers(
      @RequestParam double latitude,
      @RequestParam double longitude,
      @RequestParam(required = false) String province,
      @RequestParam(required = false) String city,
      @RequestParam(required = false) String district
  );
}

