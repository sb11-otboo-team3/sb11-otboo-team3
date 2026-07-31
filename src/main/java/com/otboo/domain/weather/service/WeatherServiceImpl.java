package com.otboo.domain.weather.service;

import com.otboo.domain.weather.cache.GridRecencyCache;
import com.otboo.domain.weather.client.KakaoLocationClient;
import com.otboo.domain.weather.client.KmaWeatherClient;
import com.otboo.domain.weather.dto.HumidityDto;
import com.otboo.domain.weather.dto.KakaoRegion;
import com.otboo.domain.weather.dto.PrecipitationDto;
import com.otboo.domain.weather.dto.TemperatureDto;
import com.otboo.domain.weather.dto.VilageFcstItem;
import com.otboo.domain.weather.dto.WeatherAPILocation;
import com.otboo.domain.weather.dto.WeatherDto;
import com.otboo.domain.weather.dto.WindSpeedDto;
import com.otboo.domain.weather.entity.Grid;
import com.otboo.domain.weather.repository.GridRepository;
import com.otboo.domain.weather.util.GridConverter;
import com.otboo.domain.weather.util.VilageFcstBaseTime;
import com.otboo.domain.weather.util.VilageFcstBaseTimeResolver;
import com.otboo.domain.weather.util.WeatherGrid;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class WeatherServiceImpl implements WeatherService {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");

  private final GridConverter gridConverter;
  private final GridRepository gridRepository;
  private final KakaoLocationClient kakaoLocationClient;
  private final GridRecencyCache gridRecencyCache;
  private final GridSaver gridSaver;
  private final VilageFcstBaseTimeResolver baseTimeResolver;
  private final KmaWeatherClient kmaWeatherClient;

  @Override
  @Transactional
  public WeatherAPILocation getLocation(double latitude, double longitude) {
    WeatherGrid grid = gridConverter.convert(latitude, longitude);

    // 카카오에서 조회
    KakaoRegion region = kakaoLocationClient.getRegion(latitude, longitude);

    if (gridRecencyCache.isRecentlyConfirmed(grid)) {
      return toDto(latitude, longitude, grid, region);
    }

    // 격자 레지스트리 갱신 (날씨 프리페치 배치가 실제로 쓰이는 격자만 골라낼 때 참고할 용도)
    Optional<Grid> existing = gridRepository.findByXAndY(grid.x(), grid.y());
    if (existing.isPresent()) {
      Grid found = existing.get();
      found.refreshRequestedAt();
      gridRepository.save(found);
    } else {
      // REQUIRES_NEW로 분리된 저장 시도가 유니크 제약 위반으로 실패해도, 그 실패는 별도 트랜잭션 안에서
      // 끝나므로 여기서 잡아도 이 메서드의 트랜잭션(바깥)엔 영향 없다.
      try {
        gridSaver.saveInNewTransaction(Grid.builder().x(grid.x()).y(grid.y()).build());
      } catch (DataIntegrityViolationException e) {
        log.warn("격자 등록 - 동시성 충돌 발생, x={}, y={}", grid.x(), grid.y(), e);
      }
    }

    gridRecencyCache.markConfirmed(grid);

    return toDto(latitude, longitude, grid, region);
  }

  @Override
  public List<WeatherDto> getWeathers(double latitude, double longitude) {
    WeatherAPILocation location = getLocation(latitude, longitude);
    VilageFcstBaseTime baseTime = baseTimeResolver.resolve(LocalDateTime.now());
    List<VilageFcstItem> forecasts = kmaWeatherClient.getForecast(location.x(), location.y(), baseTime);

    return forecasts.stream()
        .map(item -> toWeatherDto(item, location))
        .toList();
  }

  private WeatherDto toWeatherDto(VilageFcstItem item, WeatherAPILocation location) {
    return new WeatherDto(
        null,
        item.forecastedAt().atZone(KST).toInstant(),
        item.forecastAt().atZone(KST).toInstant(),
        location,
        item.skyStatus(),
        new PrecipitationDto(item.precipitationType(), item.precipitationAmount(), item.precipitationProbability()),
        new HumidityDto(item.humidity(), 0.0),
        new TemperatureDto(
            item.temperature(),
            0.0,
            item.temperatureMin() != null ? item.temperatureMin() : item.temperature(),
            item.temperatureMax() != null ? item.temperatureMax() : item.temperature()
        ),
        new WindSpeedDto(item.windSpeed(), item.windStrength())
    );
  }

  private WeatherAPILocation toDto(
      double latitude, double longitude, WeatherGrid grid, KakaoRegion region
  ) {
    return new WeatherAPILocation(
        latitude,
        longitude,
        grid.x(),
        grid.y(),
        new String[]{region.province(), region.city(), region.district()}
    );
  }
}
