package com.otboo.domain.weather.service;

import com.otboo.domain.weather.cache.GridRecencyCache;
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
import com.otboo.domain.weather.dto.WeatherSummaryDto;
import com.otboo.domain.weather.dto.WindSpeedDto;
import com.otboo.domain.weather.entity.Grid;
import com.otboo.domain.weather.entity.Weather;
import com.otboo.domain.weather.entity.WindStrength;
import com.otboo.domain.weather.exception.KmaApiException;
import com.otboo.domain.weather.repository.GridRepository;
import com.otboo.domain.weather.repository.WeatherRepository;
import com.otboo.domain.weather.util.DailyForecastSelector;
import com.otboo.domain.weather.util.GridConverter;
import com.otboo.domain.weather.util.VilageFcstBaseTime;
import com.otboo.domain.weather.util.VilageFcstBaseTimeResolver;
import com.otboo.domain.weather.util.WeatherGrid;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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

    // 위치 가져오기.
    //TODO: 프로필에 있으면 프로필 위치 정보 가져오기
    WeatherAPILocation location = getLocation(latitude, longitude);
    WeatherGrid weatherGrid = new WeatherGrid(location.x(), location.y());

    //필요한 날씨 발표 시각 걔산
    VilageFcstBaseTime baseTime = baseTimeResolver.resolve(LocalDateTime.now(clock));
    Instant forecastedAt = baseTime.baseDate().atTime(baseTime.baseTime()).atZone(KST).toInstant();

    // 캐쉬에서 찾아보기.
    Optional<List<WeatherDto>> cached = weatherForecastCache.find(weatherGrid, forecastedAt);
    List<WeatherDto> allForecasts;
    //캐시에 있으면 가져오기.
    if (cached.isPresent()) {
      log.debug("날씨 조회 - 캐시 히트, x={}, y={}, forecastedAt={}", weatherGrid.x(), weatherGrid.y(), forecastedAt);
      allForecasts = cached.get().stream()
          .map(dto -> withLocation(dto, location))
          .toList();
    } else { // 캐시에 없으면 DB에서 찾아보기.
      Grid grid = gridRepository.findByXAndY(location.x(), location.y())
          .orElseThrow(() -> new IllegalStateException("격자가 등록되어 있지 않습니다: x=" + location.x() + ", y=" + location.y()));


      List<Weather> existing = weatherRepository.findByGridAndForecastedAt(grid, forecastedAt);
      //DB에 날씨 정보 있으면 가져오고 캐시 등록
      if (!existing.isEmpty()) {
        log.debug("날씨 조회 - DB 히트, x={}, y={}, forecastedAt={}", weatherGrid.x(), weatherGrid.y(), forecastedAt);
        allForecasts = existing.stream()
            .map(weather -> toWeatherDto(weather, location))
            .toList();
        weatherForecastCache.save(weatherGrid, forecastedAt, allForecasts);
      } else {
        // DB에도 날씨 정보 없으면 기상청 API 호출
        try {
          List<VilageFcstItem> forecasts = kmaWeatherClient.getForecast(location.x(), location.y(), baseTime);
          log.debug("날씨 조회 - 기상청 API 호출, x={}, y={}, forecastedAt={}", weatherGrid.x(), weatherGrid.y(), forecastedAt);
          allForecasts = forecasts.stream()
              .map(item -> saveAndConvert(item, grid, location))
              .toList();
          weatherForecastCache.save(weatherGrid, forecastedAt, allForecasts);
        } catch (KmaApiException e) {
          // 기상청 API에서 응답을 못받았을시 이전 발표로 폴백
          log.error("기상청 호출 실패 - 이전 판으로 폴백 시도, x={}, y={}, baseTime={}",
              weatherGrid.x(), weatherGrid.y(), baseTime, e);
          allForecasts = fallbackToPreviousForecast(weatherGrid, grid, baseTime, location)
              .orElseThrow(() -> e);
        }
      }
    }

    return dailyForecastSelector.select(allForecasts, clock.instant());
  }

  @Override
  public WeatherSummaryDto getWeatherSummary(UUID weatherId) {
    Weather weather = weatherRepository.findById(weatherId)
        .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 날씨 정보입니다: id=" + weatherId));

    LocalDate date = weather.getForecastAt().atZone(KST).toLocalDate();
    Instant dayStart = date.atStartOfDay(KST).toInstant();
    Instant dayEnd = date.plusDays(1).atStartOfDay(KST).toInstant();
    WeatherGrid weatherGrid = new WeatherGrid(weather.getGrid().getX(), weather.getGrid().getY());

    TemperatureRange range = weatherForecastCache.find(weatherGrid, weather.getForecastedAt())
        .map(cached -> dailyTemperatureRangeFromCache(cached, dayStart, dayEnd))
        .orElseGet(() -> dailyTemperatureRangeFromDb(weather.getGrid(), weather.getForecastedAt(), dayStart, dayEnd));

    return weather.toSummaryDto(range.min(), range.max());
  }

  private record TemperatureRange(double min, double max) {
  }

  // 캐시엔 그날 하나가 아니라 배치 전체(여러 날짜)가 들어있어서 날짜로 한 번 더 걸러야 함
  private TemperatureRange dailyTemperatureRangeFromCache(
      List<WeatherDto> cachedForecasts, Instant dayStart, Instant dayEnd
  ) {
    List<WeatherDto> dayForecasts = cachedForecasts.stream()
        .filter(dto -> !dto.forecastAt().isBefore(dayStart) && dto.forecastAt().isBefore(dayEnd))
        .toList();
    return new TemperatureRange(
        dayForecasts.stream().mapToDouble(dto -> dto.temperature().min()).min().orElseThrow(),
        dayForecasts.stream().mapToDouble(dto -> dto.temperature().max()).max().orElseThrow()
    );
  }

  // 캐시가 만료됐을 때(TTL 3시간 지남) DB로 폴백
  private TemperatureRange dailyTemperatureRangeFromDb(
      Grid grid, Instant forecastedAt, Instant dayStart, Instant dayEnd
  ) {
    List<Weather> dayForecasts = weatherRepository.findByGridAndForecastedAtAndForecastAtBetween(
        grid, forecastedAt, dayStart, dayEnd);
    return new TemperatureRange(
        dayForecasts.stream()
            .mapToDouble(w -> w.getTemperatureMin() != null ? w.getTemperatureMin() : w.getTemperatureCurrent())
            .min().orElseThrow(),
        dayForecasts.stream()
            .mapToDouble(w -> w.getTemperatureMax() != null ? w.getTemperatureMax() : w.getTemperatureCurrent())
            .max().orElseThrow()
    );
  }

  //가장 최근 발표 시각의 데이터가 없을 경우에 그 전 데이터로 대체.
  private Optional<List<WeatherDto>> fallbackToPreviousForecast(
      WeatherGrid weatherGrid, Grid grid, VilageFcstBaseTime baseTime, WeatherAPILocation location
  ) {
    // 전타임 시간
    VilageFcstBaseTime previousBaseTime = baseTimeResolver.previous(baseTime);
    Instant previousForecastedAt = previousBaseTime.baseDate().atTime(previousBaseTime.baseTime()).atZone(KST).toInstant();

    // 캐시에서 찾기
    Optional<List<WeatherDto>> cached = weatherForecastCache.find(weatherGrid, previousForecastedAt);
    if (cached.isPresent()) {
      log.warn("이전 발표로 폴백 성공(캐시), x={}, y={}, previousBaseTime={}",
          weatherGrid.x(), weatherGrid.y(), previousBaseTime);
      return Optional.of(cached.get().stream()
          .map(dto -> withLocation(dto, location))
          .toList());
    }

    // 캐시에서 없으면 DB에서 찾기.
    List<Weather> previous = weatherRepository.findByGridAndForecastedAt(grid, previousForecastedAt);
    if (previous.isEmpty()) {
      log.error("이전 발표로 폴백 실패 - 대체 데이터 없음, x={}, y={}, previousBaseTime={}",
          weatherGrid.x(), weatherGrid.y(), previousBaseTime);
      return Optional.empty();
    }

    log.warn("이전 발표로 폴백 성공(DB), x={}, y={}, previousBaseTime={}",
        weatherGrid.x(), weatherGrid.y(), previousBaseTime);
    return Optional.of(previous.stream()
        .map(weather -> toWeatherDto(weather, location))
        .toList());
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
      Double humidityDayBefore = dayBefore.get().getHumidityCurrent();
      Double temperatureDayBefore = dayBefore.get().getTemperatureCurrent();
      if (item.humidity() != null && humidityDayBefore != null) {
        humidityComparedToDayBefore = item.humidity() - humidityDayBefore;
      }
      if (item.temperature() != null && temperatureDayBefore != null) {
        temperatureComparedToDayBefore = item.temperature() - temperatureDayBefore;
      }
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
      // 이 스레드는 저장에 실패했으니 다시 조회해서 id를 채워준다.
      return weatherRepository.findByGridAndForecastAtAndForecastedAt(grid, forecastAt, forecastedAt)
          .map(existing -> toWeatherDto(existing, location))
          .orElseGet(() -> toWeatherDto(item, location));
    }

    return toWeatherDto(weather, location);
  }

  // 객체에서 Dto
  private WeatherDto toWeatherDto(Weather weather, WeatherAPILocation location) {
    return new WeatherDto(
        weather.getId(),
        weather.getForecastedAt(),
        weather.getForecastAt(),
        location,
        weather.getSkyStatus(),
        new PrecipitationDto(
            weather.getPrecipitationType(), orElseZero(weather.getPrecipitationAmount()),
            orElseZero(weather.getPrecipitationProbability())),
        new HumidityDto(orElseZero(weather.getHumidityCurrent()), orElseZero(weather.getHumidityComparedToDayBefore())),
        new TemperatureDto(
            orElseZero(weather.getTemperatureCurrent()),
            orElseZero(weather.getTemperatureComparedToDayBefore()),
            orElseZero(weather.getTemperatureMin() != null ? weather.getTemperatureMin() : weather.getTemperatureCurrent()),
            orElseZero(weather.getTemperatureMax() != null ? weather.getTemperatureMax() : weather.getTemperatureCurrent())
        ),
        new WindSpeedDto(orElseZero(weather.getWindSpeed()), WindStrength.fromSpeed(weather.getWindSpeed()))
    );
  }

  // null이면 0.0으로 리턴
  private double orElseZero(Double value) {
    return value != null ? value : 0.0;
  }

  // 기상청 api에서 Dto
  private WeatherDto toWeatherDto(VilageFcstItem item, WeatherAPILocation location) {
    return new WeatherDto(
        null,
        item.forecastedAt().atZone(KST).toInstant(),
        item.forecastAt().atZone(KST).toInstant(),
        location,
        item.skyStatus(),
        new PrecipitationDto(item.precipitationType(), orElseZero(item.precipitationAmount()),
            orElseZero(item.precipitationProbability())),
        new HumidityDto(orElseZero(item.humidity()), 0.0),
        new TemperatureDto(
            orElseZero(item.temperature()),
            0.0,
            orElseZero(item.temperatureMin() != null ? item.temperatureMin() : item.temperature()),
            orElseZero(item.temperatureMax() != null ? item.temperatureMax() : item.temperature())
        ),
        new WindSpeedDto(orElseZero(item.windSpeed()), item.windStrength())
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
