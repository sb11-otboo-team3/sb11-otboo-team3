package com.otboo.domain.weather.service;

import com.otboo.domain.weather.dto.WeatherAPILocation;
import com.otboo.domain.weather.dto.WeatherDto;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

// getLocation/getWeathers 각각을 실제로 처리하는 컴포넌트(LocationResolver, WeatherForecastFinder)에
// 위임만 하는 얇은 파사드. 캐시/DB/기상청 조회 흐름 자체는 WeatherForecastFinder를 참고할 것.
@Slf4j
@Service
@RequiredArgsConstructor
    public class WeatherServiceImpl implements WeatherService {

    private final LocationResolver locationResolver;
    private final WeatherForecastFinder weatherForecastFinder;

    @Override
    public Mono<WeatherAPILocation> getLocation(double latitude, double longitude) {
    return locationResolver.resolve(latitude, longitude);
  }

  @Override
  public Mono<List<WeatherDto>> getWeathers(double latitude, double longitude) {
    // 위치 가져오기(카카오 호출 포함 - 논블로킹).
    //TODO: 프로필에 있으면 프로필 위치 정보 가져오기
    return locationResolver.resolve(latitude, longitude)
        .flatMap(weatherForecastFinder::find);
  }
}
