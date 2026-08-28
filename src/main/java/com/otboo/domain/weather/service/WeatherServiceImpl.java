package com.otboo.domain.weather.service;

import com.otboo.domain.weather.dto.KakaoRegion;
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
  public Mono<List<WeatherDto>> getWeathers(
      double latitude, double longitude, String province, String city, String district) {
    // province가 있으면 이미 검증된 지역명으로 보고 카카오 호출을 생략한다.
    KakaoRegion knownRegion = (province == null || province.isBlank())
        ? null
        : new KakaoRegion(province, city, district);

    return locationResolver.resolve(latitude, longitude, knownRegion)
        .flatMap(weatherForecastFinder::find);
  }
}
