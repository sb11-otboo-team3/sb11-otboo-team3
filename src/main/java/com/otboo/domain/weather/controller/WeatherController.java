package com.otboo.domain.weather.controller;

import com.otboo.domain.weather.dto.WeatherAPILocation;
import com.otboo.domain.weather.dto.WeatherDto;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@RequestMapping("/api/weathers")
public interface WeatherController {

  @GetMapping("/location")
  WeatherAPILocation getLocation(
      @RequestParam double longitude,
      @RequestParam double latitude
  );

  @GetMapping
  List<WeatherDto> getWeathers(
      @RequestParam double latitude,
      @RequestParam double longitude
  );
}

