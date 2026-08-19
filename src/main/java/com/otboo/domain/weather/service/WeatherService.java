package com.otboo.domain.weather.service;

import com.otboo.domain.weather.dto.WeatherAPILocation;
import com.otboo.domain.weather.dto.WeatherDto;
import java.util.List;
import reactor.core.publisher.Mono;

public interface WeatherService {

  Mono<WeatherAPILocation> getLocation(double latitude, double longitude);

  // province가 없는 호출은 알려진 지역명 없이(카카오 호출 경로로) 위임한다.
  default Mono<List<WeatherDto>> getWeathers(double latitude, double longitude) {
    return getWeathers(latitude, longitude, null, null, null);
  }

  // province/city/district: 호출부(로그인 사용자의 프로필 등)가 이미 알고 있는 지역명이 있으면 실어서
  // 넘긴다. province가 있으면 카카오 호출을 생략한다 - city/district는 세종시처럼 중간 단계가 없는
  // 행정구역도 있어 비어 있을 수 있다.
  Mono<List<WeatherDto>> getWeathers(
      double latitude, double longitude, String province, String city, String district);
}