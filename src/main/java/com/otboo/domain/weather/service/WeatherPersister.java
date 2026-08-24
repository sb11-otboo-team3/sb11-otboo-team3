package com.otboo.domain.weather.service;

import com.otboo.domain.weather.diff.DiffCategory;
import com.otboo.domain.weather.diff.WeatherAnnouncementDiffEvent;
import com.otboo.domain.weather.diff.WeatherDiffEvaluator;
import com.otboo.domain.weather.diff.WeatherDiffProperties;
import com.otboo.domain.weather.dto.VilageFcstItem;
import com.otboo.domain.weather.dto.WeatherAPILocation;
import com.otboo.domain.weather.dto.WeatherDto;
import com.otboo.domain.weather.entity.Grid;
import com.otboo.domain.weather.entity.Weather;
import com.otboo.domain.weather.repository.WeatherRepository;
import com.otboo.domain.weather.util.VilageFcstBaseTime;
import com.otboo.domain.weather.util.VilageFcstBaseTimeResolver;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// 기상청 응답 항목 하나를 저장하고 응답 DTO로 변환한다. 온디맨드 조회 흐름(WeatherForecastFinder)과
// 나중에 붙을 배치가 똑같이 재사용할 수 있도록 저장 로직만 따로 뺀 것.
@Slf4j
@Component
@RequiredArgsConstructor
public class WeatherPersister {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");

  private final WeatherRepository weatherRepository;
  private final WeatherSaver weatherSaver;
  private final WeatherDiffEvaluator weatherDiffEvaluator;
  private final VilageFcstBaseTimeResolver baseTimeResolver;
  private final WeatherDiffProperties weatherDiffProperties;
  private final ApplicationEventPublisher eventPublisher;

  public Optional<WeatherDto> persist(VilageFcstItem item, Grid grid, WeatherAPILocation location) {
    return persistEntity(item, grid).map(saved -> saved.toDto(location));
  }

  // 배치(WeatherPrefetch 등)처럼 응답 DTO가 필요 없는 호출부를 위한 오버로드. 위치 정보를 지어낼 필요 없이
  // 저장된 엔티티만 그대로 돌려준다.
  public Optional<Weather> persist(VilageFcstItem item, Grid grid) {
    return persistEntity(item, grid);
  }

  private Optional<Weather> persistEntity(VilageFcstItem item, Grid grid) {
    Instant forecastAt = item.forecastAt().atZone(KST).toInstant();
    Instant forecastedAt = item.forecastedAt().atZone(KST).toInstant();

    // 기상청이 매핑 안 되는(혹은 응답에 아예 없는) 상태 코드를 내려주면 sky_status/precipitation_type이
    // null이 되는데, 두 컬럼 다 NOT NULL이라 그대로 저장하면 DataIntegrityViolationException.
    // 임의로 기본값을 지어내는 대신 이 시간대 항목만 건너뛴다.
    if (item.skyStatus() == null || item.precipitationType() == null) {
      log.warn("미지원 기상청 상태 코드 - 예보 항목 스킵, grid=({},{}), forecastAt={}, forecastedAt={}, skyStatus={}, precipitationType={}",
          grid.getX(), grid.getY(), forecastAt, forecastedAt, item.skyStatus(), item.precipitationType());
      return Optional.empty();
    }

    // 전일 대비 조회(dayBefore)와 발표별 diff 비교 대상 조회(previousAnnouncement)는 서로 다른
    // forecastAt을 보지만, 같은 (grid, forecast_at) 유니크 인덱스를 타는 점 조회라 IN절 하나로 묶어
    // DB 왕복을 한 번으로 줄인다.
    Instant dayBeforeForecastAt = forecastAt.minus(1, ChronoUnit.DAYS);
    Map<Instant, Weather> existingByForecastAt = weatherRepository
        .findByGridAndForecastAtIn(grid, List.of(dayBeforeForecastAt, forecastAt))
        .stream()
        .collect(Collectors.toMap(Weather::getForecastAt, weather -> weather));

    Double humidityComparedToDayBefore = null;
    Double temperatureComparedToDayBefore = null;
    Optional<Weather> dayBefore = Optional.ofNullable(existingByForecastAt.get(dayBeforeForecastAt));
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

    // 발표별 급변 비교 대상 - 같은 (grid, forecastAt)의 이전 값. upsert가 덮어쓰기 전에 미리 조회해둬야
    // "이전 값"을 알 수 있다(덮어쓴 뒤엔 사라짐).
    Optional<Weather> previousAnnouncement = Optional.ofNullable(existingByForecastAt.get(forecastAt));

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

    // 같은 (grid, forecastAt)에 이미 row가 있으면(예: 예전 배치가 이미 이 시간대를 예측해놨으면) upsert가
    // 알아서 최신 값으로 덮어쓴다 - 유니크 위반을 신경 쓸 필요가 없어짐(WeatherSaver 참고).
    Weather saved = weatherSaver.upsertInNewTransaction(weather);

    publishAnnouncementDiffIfTriggered(item, grid, forecastAt, previousAnnouncement, saved);

    return Optional.of(saved);
  }

  // 발표별 급변: "다음 발표 전 시간대"만 비교 대상으로 삼는다 - 그보다 먼 미래는 다음 배치가 다시
  // 검증할 기회가 있으니 지금 당장 알릴 필요가 없다(같은 forecastAt이 여러 번 재평가되며 반복
  // 알림이 나가는 것도 이 스코프 제한으로 원천 차단됨).
  //
  // 이 메서드는 절대 예외를 던지지 않는다 - 호출 시점엔 이미 weather row가 커밋된 뒤라서, 여기서
  // 실패가 새어나가면 "저장은 됐는데 배치/요청은 실패"하는 애매한 상태가 된다. 특히 baseTimeResolver.next()는
  // item.forecastedAt()이 정규 발표시각이 아니면 예외를 던지는데(VilageFcstBaseTimeResolver 참고),
  // 이 값은 기상청 원본을 검증 없이 그대로 쓰는 값이다 - 배치의 skip 정책은 KmaApiException만
  // 대상이라 이 예외는 격자만 건너뛰지 못하고 Step 전체를 실패시킨다. 알림 계산 실패가 저장/배치
  // 결과에 영향을 주면 안 되므로 통째로 흡수한다.
  private void publishAnnouncementDiffIfTriggered(
      VilageFcstItem item, Grid grid, Instant forecastAt, Optional<Weather> previousAnnouncement, Weather current
  ) {
    if (previousAnnouncement.isEmpty()) {
      return;
    }

    try {
      VilageFcstBaseTime currentBaseTime = new VilageFcstBaseTime(
          item.forecastedAt().toLocalDate(), item.forecastedAt().toLocalTime());
      VilageFcstBaseTime nextBaseTime = baseTimeResolver.next(currentBaseTime);
      Instant nextAnnouncementAt = nextBaseTime.baseDate().atTime(nextBaseTime.baseTime()).atZone(KST).toInstant();
      if (!weatherDiffEvaluator.isWithinNextAnnouncementWindow(forecastAt, nextAnnouncementAt)) {
        return;
      }

      Weather previous = previousAnnouncement.get();
      Set<DiffCategory> triggeredCategories = EnumSet.noneOf(DiffCategory.class);

      // null(결측치) 가드는 WeatherDiffEvaluator 안으로 옮겨져 있다 - 여기서 또 확인할 필요 없음.
      if (weatherDiffEvaluator.isTemperatureTriggered(
          previous.getTemperatureCurrent(), current.getTemperatureCurrent(), weatherDiffProperties)) {
        triggeredCategories.add(DiffCategory.TEMPERATURE);
      }
      if (weatherDiffEvaluator.isPrecipitationTriggered(previous.getPrecipitationType(), current.getPrecipitationType())) {
        triggeredCategories.add(DiffCategory.PRECIPITATION);
      }
      if (weatherDiffEvaluator.isWindTriggered(previous.getWindSpeed(), current.getWindSpeed())) {
        triggeredCategories.add(DiffCategory.WIND);
      }

      if (!triggeredCategories.isEmpty()) {
        eventPublisher.publishEvent(new WeatherAnnouncementDiffEvent(previous, current, triggeredCategories));
      }
    } catch (RuntimeException e) {
      log.error("발표별 급변 판정 실패 - weather row는 이미 저장됨, 이번 판정만 건너뜀, grid=({},{}), forecastAt={}, forecastedAt={}",
          grid.getX(), grid.getY(), forecastAt, item.forecastedAt(), e);
    }
  }

  // 그 날짜(grid+date)의 min/max를 "계산만" 한다(쓰지 않음). 기상청이 그 날짜 어딘가에 실제 TMN/TMX를
  // 실어줬으면 그 값을 그대로 신뢰하고, 하나도 없으면(배치 경계에 걸린 날 등) 그 날짜 전체 row의
  // temperature(현재기온) 값들로 직접 계산한다 - row를 앱으로 끌어오지 않고 DB가 집계까지 끝내서 값
  // 2개만 돌려준다(WeatherRepository.findDailyTemperatureRange). 이 시점에 grid+날짜로 조회하니까
  // 방금 새로 저장한 row뿐 아니라 이전에 이미 저장돼 있던 row(예: 오늘 이미 지나간 시간대)까지 다
  // 포함된다. 응답을 만드는 동기 경로에서 쓰이는 부분이라 여기선 DB에 쓰지 않는다(persistDailyMinMax가 담당).
  @Transactional(readOnly = true)
  public Optional<DailyTemperatureRange> resolveDailyMinMax(Grid grid, LocalDate date) {
    Instant dayStart = date.atStartOfDay(KST).toInstant();
    Instant dayEnd = date.plusDays(1).atStartOfDay(KST).toInstant();

    WeatherRepository.DailyTemperatureRangeProjection resolved =
        weatherRepository.findDailyTemperatureRange(grid.getId(), dayStart, dayEnd);

    if (resolved == null || resolved.getResolvedMin() == null || resolved.getResolvedMax() == null) {
      log.warn("일 최저/최고기온 확정 실패 - 이 날짜에 유효한 기온 데이터가 하나도 없음, grid={}, date={}",
          grid.getId(), date);
      return Optional.empty();
    }

    return Optional.of(new DailyTemperatureRange(resolved.getResolvedMin(), resolved.getResolvedMax()));
  }

  // resolveDailyMinMax가 확정한 값을 그 날짜에 속한 모든 row에 통일해서 써넣는다. 이번 응답 자체엔
  // 이미 위에서 값을 확보해서 반영했으니 영향이 없는, "나중에 다른 요청이 DB를 읽을 때를 위한" 정리
  // 작업이다 - 그래서 호출부(WeatherForecastFinder)가 응답을 기다리지 않고 백그라운드로 실행한다.
  // REQUIRES_NEW인 이유: WeatherPrefetch 배치의 tasklet처럼 바깥에 이미 트랜잭션이 열려있는 호출부에서도,
  // 이 정리 작업 하나 때문에 바깥 트랜잭션(여러 격자를 순회하는 동안 열려있을 수 있음)에 얹혀 커넥션을
  // 오래 붙들지 않고 독립적으로 커밋되게 하려는 것 - WeatherSaver.upsertInNewTransaction과 같은 이유.
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void persistDailyMinMax(Grid grid, LocalDate date, double min, double max) {
    Instant dayStart = date.atStartOfDay(KST).toInstant();
    Instant dayEnd = date.plusDays(1).atStartOfDay(KST).toInstant();
    weatherRepository.updateDailyTemperatureRange(grid, min, max, dayStart, dayEnd);
  }

  public record DailyTemperatureRange(double min, double max) {
  }
}
