package com.otboo.domain.weather.service;

import com.otboo.domain.weather.cache.GridRecencyCache;
import com.otboo.domain.weather.cache.WeatherForecastCache;
import com.otboo.domain.weather.cache.WeatherForecastCache;
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
import com.otboo.domain.weather.entity.Weather;
import com.otboo.domain.weather.entity.WindStrength;
import com.otboo.domain.weather.repository.GridRepository;
import com.otboo.domain.weather.repository.WeatherRepository;
import com.otboo.domain.weather.util.DailyForecastSelector;
import com.otboo.domain.weather.util.GridConverter;
import com.otboo.domain.weather.util.VilageFcstBaseTime;
import com.otboo.domain.weather.util.VilageFcstBaseTimeResolver;
import com.otboo.domain.weather.util.WeatherGrid;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
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
  private final WeatherRepository weatherRepository;
  private final WeatherSaver weatherSaver;
  private final DailyForecastSelector dailyForecastSelector;
  private final Clock clock;
  private final WeatherForecastCache weatherForecastCache;

  @Override
  @Transactional
  public WeatherAPILocation getLocation(double latitude, double longitude) {
    WeatherGrid grid = gridConverter.convert(latitude, longitude);

    // 카카오에서 조회
    KakaoRegion region = kakaoLocationClient.getRegion(latitude, longitude);

    //최근 접근 한적 있는 격자인지 캐시에서 체크.(너무 많은 최근 접근한 지역인지 DB 접근을 줄이기 위해)
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
    WeatherGrid weatherGrid = new WeatherGrid(location.x(), location.y());

    VilageFcstBaseTime baseTime = baseTimeResolver.resolve(LocalDateTime.now(clock));
    Instant forecastedAt = baseTime.baseDate().atTime(baseTime.baseTime()).atZone(KST).toInstant();

    Optional<List<WeatherDto>> cached = weatherForecastCache.find(weatherGrid, forecastedAt);
    List<WeatherDto> allForecasts;
    if (cached.isPresent()) {
      allForecasts = cached.get().stream()
          .map(dto -> withLocation(dto, location))
          .toList();
    } else {
      Grid grid = gridRepository.findByXAndY(location.x(), location.y())
          .orElseThrow(() -> new IllegalStateException("격자가 등록되어 있지 않습니다: x=" + location.x() + ", y=" + location.y()));

      List<Weather> existing = weatherRepository.findByGridAndForecastedAt(grid, forecastedAt);
      if (!existing.isEmpty()) {
        allForecasts = existing.stream()
            .map(weather -> toWeatherDto(weather, location))
            .toList();
      } else {
        List<VilageFcstItem> forecasts = kmaWeatherClient.getForecast(location.x(), location.y(), baseTime);
        allForecasts = forecasts.stream()
            .map(item -> saveAndConvert(item, grid, location))
            .toList();
      }
      weatherForecastCache.save(weatherGrid, forecastedAt, allForecasts);
    }

    return dailyForecastSelector.select(allForecasts, clock.instant());
  }

  private WeatherDto withLocation(WeatherDto dto, WeatherAPILocation location) {
    return new WeatherDto(
        dto.id(),
        dto.forecastedAt(),
        dto.forecastAt(),
        location,
        dto.skyStatus(),
        dto.precipitation(),
        dto.humidity(),
        dto.temperature(),
        dto.windSpeed()
    );
  }

  private WeatherDto saveAndConvert(VilageFcstItem item, Grid grid, WeatherAPILocation location) {
    Instant forecastAt = item.forecastAt().atZone(KST).toInstant();
    Instant forecastedAt = item.forecastedAt().atZone(KST).toInstant();

    Double humidityComparedToDayBefore = null;
    Double temperatureComparedToDayBefore = null;
    Optional<Weather> dayBefore = weatherRepository.findByGridAndForecastAt(grid, forecastAt.minus(1, ChronoUnit.DAYS));
    if (dayBefore.isPresent()) {
      humidityComparedToDayBefore = item.humidity() - dayBefore.get().getHumidityCurrent();
      temperatureComparedToDayBefore = item.temperature() - dayBefore.get().getTemperatureCurrent();
    }

    Weather weather = Weather.builder()
        .grid(grid)
        .forecastedAt(forecastedAt)
        .forecastAt(forecastAt)
        .skyStatus(item.skyStatus())
        .precipitationType(item.precipitationType())
        .precipitationAmount(item.precipitationAmount())
        .precipitationProbability(item.precipitationProbability())
        .humidityCurrent(item.humidity())
        .humidityComparedToDayBefore(humidityComparedToDayBefore)
        .temperatureCurrent(item.temperature())
        .temperatureComparedToDayBefore(temperatureComparedToDayBefore)
        .temperatureMin(item.temperatureMin())
        .temperatureMax(item.temperatureMax())
        .windSpeed(item.windSpeed())
        .build();

    // REQUIRES_NEW로 분리된 저장 시도가 유니크 제약 위반으로 실패해도, 그 실패는 별도 트랜잭션 안에서
    // 끝나므로 여기서 잡아도 이 메서드의 트랜잭션(바깥)엔 영향 없다.
    try {
      weatherSaver.saveInNewTransaction(weather);
    } catch (DataIntegrityViolationException e) {
      log.warn("날씨 저장 - 동시성 충돌 발생, grid={}, forecastAt={}, forecastedAt={}",
          grid.getId(), forecastAt, forecastedAt, e);
      return toWeatherDto(item, location);
    }

    return toWeatherDto(weather, location);
  }

  private WeatherDto toWeatherDto(Weather weather, WeatherAPILocation location) {
    return new WeatherDto(
        weather.getId(),
        weather.getForecastedAt(),
        weather.getForecastAt(),
        location,
        weather.getSkyStatus(),
        new PrecipitationDto(
            weather.getPrecipitationType(), weather.getPrecipitationAmount(), weather.getPrecipitationProbability()),
        new HumidityDto(weather.getHumidityCurrent(), orElseZero(weather.getHumidityComparedToDayBefore())),
        new TemperatureDto(
            weather.getTemperatureCurrent(),
            orElseZero(weather.getTemperatureComparedToDayBefore()),
            weather.getTemperatureMin() != null ? weather.getTemperatureMin() : weather.getTemperatureCurrent(),
            weather.getTemperatureMax() != null ? weather.getTemperatureMax() : weather.getTemperatureCurrent()
        ),
        new WindSpeedDto(weather.getWindSpeed(), WindStrength.fromSpeed(weather.getWindSpeed()))
    );
  }

  private double orElseZero(Double value) {
    return value != null ? value : 0.0;
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
