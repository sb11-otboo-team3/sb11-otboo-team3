package com.otboo.domain.weather.controller;

import com.otboo.domain.weather.dto.WeatherAPILocation;
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
}

