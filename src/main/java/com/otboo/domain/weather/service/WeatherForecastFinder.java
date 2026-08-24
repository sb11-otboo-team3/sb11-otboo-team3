package com.otboo.domain.weather.service;

import com.otboo.domain.weather.cache.WeatherForecastCache;
import com.otboo.domain.weather.client.KmaWeatherClient;
import com.otboo.domain.weather.dto.TemperatureDto;
import com.otboo.domain.weather.dto.VilageFcstItem;
import com.otboo.domain.weather.dto.WeatherAPILocation;
import com.otboo.domain.weather.dto.WeatherDto;
import com.otboo.domain.weather.entity.Grid;
import com.otboo.domain.weather.entity.Weather;
import com.otboo.domain.weather.exception.KmaApiException;
import com.otboo.domain.weather.repository.WeatherRepository;
import com.otboo.domain.weather.util.DailyForecastSelector;
import com.otboo.domain.weather.util.VilageFcstBaseTime;
import com.otboo.domain.weather.util.VilageFcstBaseTimeResolver;
import com.otboo.domain.weather.util.WeatherGrid;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

// 캐시/DB/기상청 3단 폴백으로 특정 위치의 날씨 예보를 찾아온다. WeatherServiceImpl.getWeathers()가
// 위치를 해석한 뒤 이 클래스에 위임하는 구조 - 이 흐름 자체를 배치에서도 재사용할 여지를 남겨둔 것.
@Slf4j
@Component
@RequiredArgsConstructor
public class WeatherForecastFinder {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");
  // 항목 저장/날짜별 min-max 계산을 동시에 몇 개까지 처리할지 - 커넥션 풀(기본 10개) 여유를 남겨두려고 보수적으로 잡음.
  private static final int DB_CONCURRENCY = 5;

  private final GridResolver gridResolver;
  private final VilageFcstBaseTimeResolver baseTimeResolver;
  private final KmaWeatherClient kmaWeatherClient;
  private final WeatherRepository weatherRepository;
  private final WeatherPersister weatherPersister;
  private final DailyForecastSelector dailyForecastSelector;
  private final Clock clock;
  private final WeatherForecastCache weatherForecastCache;

  // 위치를 알고 난 다음의 캐시/DB/기상청 조회 로직 전체. JPA/Redis처럼 진짜 블로킹인 구간만
  // Mono.fromCallable(...).subscribeOn(boundedElastic)으로 개별적으로 감싸고, 기상청 호출은
  // WebClient가 만들어주는 Mono를 그대로 flatMap으로 이어받는다(.block() 금지) - 응답을 기다리는
  // 동안 boundedElastic 스레드를 하나도 붙잡아두지 않기 위함(여러 요청이 동시에 기상청 응답을
  // 기다려야 하는 상황에서 스레드 풀이 불필요하게 고갈되는 걸 막는다).
  public Mono<List<WeatherDto>> find(WeatherAPILocation location) {
    WeatherGrid weatherGrid = new WeatherGrid(location.x(), location.y());

    //필요한 날씨 발표 시각 걔산
    VilageFcstBaseTime baseTime = baseTimeResolver.resolve(LocalDateTime.now(clock));
    Instant forecastedAt = baseTime.baseDate().atTime(baseTime.baseTime()).atZone(KST).toInstant();

    // 캐쉬에서 찾아보기 - 캐시엔 이 발표의 날짜별 대표 예보 전체가 한 덩어리로 들어있어서, 히트하면
    // 재계산 없이 그대로 씀(하나의 키라 부분적으로만 있는 상태는 있을 수 없음 - 전부 있거나 전부 없거나).
    return Mono.fromCallable(() -> findCachedForecasts(weatherGrid, forecastedAt))
        .subscribeOn(Schedulers.boundedElastic())
        .flatMap(cached -> cached.isEmpty()
            ? fetchFromDbOrKma(weatherGrid, location, baseTime, forecastedAt)
            : cacheHit(cached, weatherGrid, forecastedAt, location));
  }

  private List<WeatherDto> findCachedForecasts(WeatherGrid weatherGrid, Instant forecastedAt) {
    return weatherForecastCache.find(weatherGrid, forecastedAt).orElseGet(List::of);
  }

  //캐시에 있으면 가져오기.
  private Mono<List<WeatherDto>> cacheHit(
      List<WeatherDto> cached, WeatherGrid weatherGrid, Instant forecastedAt, WeatherAPILocation location
  ) {
    log.debug("날씨 조회 - 캐시 히트, x={}, y={}, forecastedAt={}", weatherGrid.x(), weatherGrid.y(), forecastedAt);
    return Mono.just(cached.stream().map(dto -> withLocation(dto, location)).toList());
  }

  private record GridAndExisting(Grid grid, List<Weather> existing) {
  }

  // 캐시에 없으면 DB에서 찾아보기.
  private Mono<List<WeatherDto>> fetchFromDbOrKma(
      WeatherGrid weatherGrid, WeatherAPILocation location, VilageFcstBaseTime baseTime, Instant forecastedAt
  ) {
    return Mono.fromCallable(() -> {
          Grid grid = gridResolver.findOrRegister(weatherGrid);
          List<Weather> existing = weatherRepository.findByGridAndForecastedAt(grid, forecastedAt);
          return new GridAndExisting(grid, existing);
        })
        .subscribeOn(Schedulers.boundedElastic())
        .flatMap(found -> found.existing().isEmpty()
            ? fetchFromKma(found.grid(), weatherGrid, location, baseTime, forecastedAt)
            : dbHit(found.existing(), weatherGrid, forecastedAt, location));
  }

  //DB에 날씨 정보 있으면 가져오고 캐시 등록
  private Mono<List<WeatherDto>> dbHit(
      List<Weather> existing, WeatherGrid weatherGrid, Instant forecastedAt, WeatherAPILocation location
  ) {
    log.debug("날씨 조회 - DB 히트, x={}, y={}, forecastedAt={}", weatherGrid.x(), weatherGrid.y(), forecastedAt);
    List<WeatherDto> allForecasts = existing.stream()
        .map(weather -> weather.toDto(location))
        .toList();
    List<WeatherDto> dailyForecasts = dailyForecastSelector.select(allForecasts, clock.instant());
    return saveToCache(weatherGrid, forecastedAt, dailyForecasts);
  }

  // DB에도 날씨 정보 없으면 기상청 API 호출.
  private Mono<List<WeatherDto>> fetchFromKma(
      Grid grid, WeatherGrid weatherGrid, WeatherAPILocation location, VilageFcstBaseTime baseTime, Instant forecastedAt
  ) {
    // Mono.defer로 감싸는 이유: kmaWeatherClient.getForecast(...) 호출 자체가 (테스트 목이나 다른
    // 이유로) 동기적으로 예외를 던지면, defer 없이는 그 예외가 뒤에 붙인 onErrorResume을 거치지도
    // 못하고 그대로 튀어나간다. defer는 실제 구독 시점까지 호출을 미루고, 그 순간 던져진 예외도
    // 정상적인 리액티브 에러 신호로 변환해줘서 이후 체인이 전부 제대로 동작하게 한다.
    return Mono.defer(() -> kmaWeatherClient.getForecast(location.x(), location.y(), baseTime))
        .doOnNext(forecasts -> log.debug("날씨 조회 - 기상청 API 호출, x={}, y={}, forecastedAt={}",
            weatherGrid.x(), weatherGrid.y(), forecastedAt))
        .flatMap(forecasts -> persistAndResolveDailyRanges(forecasts, grid, location))
        .map(allForecasts -> dailyForecastSelector.select(allForecasts, clock.instant()))
        .flatMap(dailyForecasts -> saveToCache(weatherGrid, forecastedAt, dailyForecasts))
        // 기상청 API에서 응답을 못받았을시 이전 발표로 폴백
        .onErrorResume(KmaApiException.class, e -> {
          log.warn("기상청 호출 실패 - 이전 판으로 폴백 시도, x={}, y={}, baseTime={}",
              weatherGrid.x(), weatherGrid.y(), baseTime);
          return fallbackToPreviousForecast(weatherGrid, grid, baseTime, location)
              .switchIfEmpty(Mono.error(e));
        });
  }

  // 항목들을 동시에(최대 DB_CONCURRENCY개씩) 저장한 뒤, 그 항목들이 걸치는 날짜마다 min/max를 동시에
  // 계산해서 응답용 DTO에 반영한다. min/max를 DB에 실제로 퍼뜨려 쓰는 것은 응답과 무관하므로 기다리지 않는다.
  private Mono<List<WeatherDto>> persistAndResolveDailyRanges(
      List<VilageFcstItem> forecasts, Grid grid, WeatherAPILocation location
  ) {
    return Flux.fromIterable(forecasts)
        .flatMap(item -> Mono.fromCallable(() -> weatherPersister.persist(item, grid, location))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(Mono::justOrEmpty),
            DB_CONCURRENCY)
        .collectList()
        .flatMap(persisted -> resolveDailyRanges(forecasts, grid)
            .doOnNext(resolvedRanges -> persistDailyRangesInBackground(grid, resolvedRanges))
            .map(resolvedRanges -> applyResolvedRanges(persisted, resolvedRanges)));
  }

  // 이번 항목들이 걸치는 날짜마다(동시에, 최대 DB_CONCURRENCY개씩) min/max를 계산만 한다(DB에 안 씀).
  private Mono<Map<LocalDate, WeatherPersister.DailyTemperatureRange>> resolveDailyRanges(
      List<VilageFcstItem> forecasts, Grid grid
  ) {
    List<LocalDate> dates = forecasts.stream()
        .map(item -> item.forecastAt().atZone(KST).toLocalDate())
        .distinct()
        .toList();

    return Flux.fromIterable(dates)
        .flatMap(date -> Mono.fromCallable(() -> weatherPersister.resolveDailyMinMax(grid, date))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(range -> Mono.justOrEmpty(range.map(r -> Map.entry(date, r)))),
            DB_CONCURRENCY)
        .collectMap(Map.Entry::getKey, Map.Entry::getValue);
  }

  // 계산한 min/max를 그 날짜 모든 row에 실제로 써넣는다 - 이번 응답과는 무관한 뒷정리라 구독만 하고
  // 기다리지 않는다. 실패해도 다음에 이 날짜가 다시 조회되면 그때 다시 계산되니 응답에 영향 없음.
  private void persistDailyRangesInBackground(
      Grid grid, Map<LocalDate, WeatherPersister.DailyTemperatureRange> resolvedRanges
  ) {
    resolvedRanges.forEach((date, range) ->
        Mono.fromRunnable(() -> weatherPersister.persistDailyMinMax(grid, date, range.min(), range.max()))
            .subscribeOn(Schedulers.boundedElastic())
            .doOnError(e -> log.error(
                "일 최저/최고기온 DB 반영 실패 - 다음에 이 날짜가 다시 조회되면 그때 다시 계산됨, grid={}, date={}",
                grid.getId(), date, e))
            .onErrorResume(e -> Mono.empty())
            .subscribe());
  }

  // 계산된 날짜별 min/max를 그 날짜에 속한 응답 DTO들에 반영한다.
  private List<WeatherDto> applyResolvedRanges(
      List<WeatherDto> persisted, Map<LocalDate, WeatherPersister.DailyTemperatureRange> resolvedRanges
  ) {
    return persisted.stream()
        .map(dto -> {
          WeatherPersister.DailyTemperatureRange range =
              resolvedRanges.get(dto.forecastAt().atZone(KST).toLocalDate());
          if (range == null) {
            return dto;
          }
          TemperatureDto updatedTemperature = new TemperatureDto(
              dto.temperature().current(),
              dto.temperature().comparedToDayBefore(),
              range.min(),
              range.max(),
              dto.temperature().average()
          );
          return new WeatherDto(
              dto.id(), dto.forecastedAt(), dto.forecastAt(), dto.location(),
              dto.skyStatus(), dto.precipitation(), dto.humidity(), updatedTemperature, dto.windSpeed()
          );
        })
        .toList();
  }

  // 날짜별 대표 예보 전체(dailyForecasts, 이미 하루에 하나씩만 있는 상태)를 이 발표 하나의 키에 통째로 씀.
  private Mono<List<WeatherDto>> saveToCache(
      WeatherGrid weatherGrid, Instant forecastedAt, List<WeatherDto> dailyForecasts
  ) {
    return Mono.<Void>fromRunnable(() -> weatherForecastCache.save(weatherGrid, forecastedAt, dailyForecasts))
        .subscribeOn(Schedulers.boundedElastic())
        .thenReturn(dailyForecasts);
  }

  //가장 최근 발표 시각의 데이터가 없을 경우에 그 전 데이터로 대체.
  // 대체할 데이터를 못 찾으면 Mono.empty()로 완료해서, 호출부가 switchIfEmpty로 원래 예외를
  // 그대로 전파하게 한다(기존 Optional.empty() + orElseThrow(() -> e)와 동일한 의미).
  private Mono<List<WeatherDto>> fallbackToPreviousForecast(
      WeatherGrid weatherGrid, Grid grid, VilageFcstBaseTime baseTime, WeatherAPILocation location
  ) {
    // 전타임 시간
    VilageFcstBaseTime previousBaseTime = baseTimeResolver.previous(baseTime);
    Instant previousForecastedAt = previousBaseTime.baseDate().atTime(previousBaseTime.baseTime()).atZone(KST).toInstant();

    // 캐시에서 찾기 (메인 흐름과 동일한 방식 - 이 발표 키가 통째로 있는지만 확인)
    return Mono.fromCallable(() -> findCachedForecasts(weatherGrid, previousForecastedAt))
        .subscribeOn(Schedulers.boundedElastic())
        .flatMap(cached -> {
          if (!cached.isEmpty()) {
            log.warn("이전 발표로 폴백 성공(캐시), x={}, y={}, previousBaseTime={}",
                weatherGrid.x(), weatherGrid.y(), previousBaseTime);
            return Mono.just(cached.stream()
                .map(dto -> withLocation(dto, location))
                .toList());
          }

          // 캐시에서 없으면 DB에서 찾기.
          return Mono.fromCallable(() -> weatherRepository.findByGridAndForecastedAt(grid, previousForecastedAt))
              .subscribeOn(Schedulers.boundedElastic())
              .flatMap(previous -> {
                if (previous.isEmpty()) {
                  log.error("이전 발표로 폴백 실패 - 대체 데이터 없음, x={}, y={}, previousBaseTime={}",
                      weatherGrid.x(), weatherGrid.y(), previousBaseTime);
                  return Mono.<List<WeatherDto>>empty();
                }

                log.warn("이전 발표로 폴백 성공(DB), x={}, y={}, previousBaseTime={}",
                    weatherGrid.x(), weatherGrid.y(), previousBaseTime);
                List<WeatherDto> allForecasts = previous.stream()
                    .map(weather -> weather.toDto(location))
                    .toList();
                return Mono.just(dailyForecastSelector.select(allForecasts, clock.instant()));
              });
        });
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
}
