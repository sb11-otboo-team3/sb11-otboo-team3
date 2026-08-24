package com.otboo.domain.weather.repository;

import com.otboo.domain.weather.entity.Grid;
import com.otboo.domain.weather.entity.Weather;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WeatherRepository extends JpaRepository<Weather, UUID> {

  // 이 발표(forecastedAt)가 이 grid에 대해 이미 처리됐는지 확인용(WeatherForecastFinder의 DB 히트 체크).
  // upsert 구조에서도 유효함 - 이 발표가 갱신한 row들만 이 조건에 걸림.
  List<Weather> findByGridAndForecastedAt(Grid grid, Instant forecastedAt);

  // 유니크 키가 (grid_id, forecast_at)라 이 시각에 대한 row는 최대 하나 - 전일 대비 계산(WeatherPersister)에 씀.
  Optional<Weather> findByGridAndForecastAt(Grid grid, Instant forecastAt);

  // 위와 같은 유니크 키를 여러 시각에 대해 한 번에 조회한다(WeatherPersister.persistEntity) - 전일 대비
  // 조회와 발표별 diff 조회가 서로 다른 forecastAt을 보지만 한 번의 왕복으로 묶을 수 있다.
  List<Weather> findByGridAndForecastAtIn(Grid grid, Collection<Instant> forecastAts);

  // 발표(forecastedAt)와 무관하게, 같은 grid+날짜에 걸리는 행 전부 - 일 최저/최고기온 집계(reconcileDailyMinMax)와
  // WeatherSummaryFinder의 캐시 미스 폴백에서 공용으로 씀. upsert 구조라 시간대별로 row가 하나씩만 있어서
  // 중복 없이 그 날짜의 "현재 알려진 가장 최신" 예보 전체를 그대로 가져오는 것과 같다.
  // [forecastAtStart, forecastAtEndExclusive) 반열림 구간.
  List<Weather> findByGridAndForecastAtGreaterThanEqualAndForecastAtLessThan(
      Grid grid, Instant forecastAtStart, Instant forecastAtEndExclusive);

  // 위와 같은 조건을 여러 grid에 대해 한 번의 IN절 쿼리로 묶어 조회한다(WeatherDailyDiffScheduler) -
  // 격자마다 따로 쿼리하는 대신 한 번에 가져와 애플리케이션에서 grid별로 묶어 쓴다.
  List<Weather> findByGridInAndForecastAtGreaterThanEqualAndForecastAtLessThan(
      List<Grid> grids, Instant forecastAtStart, Instant forecastAtEndExclusive);

  // 같은 (grid_id, forecast_at)에 이미 row가 있으면 최신 값으로 덮어쓰고(id/created_at은 유지),
  // 없으면 새로 만든다. RETURNING으로 결과 row를 한 번의 왕복으로 그대로 받아온다.
  // WHERE 절: 기존 row의 forecasted_at보다 이번 값이 과거면(늦게 도착한 옛날 배치 등) 덮어쓰지 않는다 -
  // 이 조건에 걸려 UPDATE가 스킵되면 RETURNING이 0행이라 결과가 비어있을 수 있음(Optional로 표현).
  @Query(value = """
      INSERT INTO weathers (
          id, grid_id, forecasted_at, forecast_at, sky_status, precipitation_type,
          precipitation_amount, precipitation_probability, humidity_current, humidity_compared_to_day_before,
          temperature_current, temperature_compared_to_day_before, temperature_min, temperature_max,
          wind_speed, created_at
      ) VALUES (
          :id, :gridId, :forecastedAt, :forecastAt, :skyStatus, :precipitationType,
          :precipitationAmount, :precipitationProbability, :humidityCurrent, :humidityComparedToDayBefore,
          :temperatureCurrent, :temperatureComparedToDayBefore, :temperatureMin, :temperatureMax,
          :windSpeed, now()
      )
      ON CONFLICT (grid_id, forecast_at) DO UPDATE SET
          forecasted_at = EXCLUDED.forecasted_at,
          sky_status = EXCLUDED.sky_status,
          precipitation_type = EXCLUDED.precipitation_type,
          precipitation_amount = EXCLUDED.precipitation_amount,
          precipitation_probability = EXCLUDED.precipitation_probability,
          humidity_current = EXCLUDED.humidity_current,
          humidity_compared_to_day_before = EXCLUDED.humidity_compared_to_day_before,
          temperature_current = EXCLUDED.temperature_current,
          temperature_compared_to_day_before = EXCLUDED.temperature_compared_to_day_before,
          temperature_min = EXCLUDED.temperature_min,
          temperature_max = EXCLUDED.temperature_max,
          wind_speed = EXCLUDED.wind_speed
      WHERE weathers.forecasted_at <= EXCLUDED.forecasted_at
      RETURNING *
      """, nativeQuery = true)
  Optional<Weather> upsert(
      @Param("id") UUID id,
      @Param("gridId") UUID gridId,
      @Param("forecastedAt") Instant forecastedAt,
      @Param("forecastAt") Instant forecastAt,
      @Param("skyStatus") String skyStatus,
      @Param("precipitationType") String precipitationType,
      @Param("precipitationAmount") Double precipitationAmount,
      @Param("precipitationProbability") Double precipitationProbability,
      @Param("humidityCurrent") Double humidityCurrent,
      @Param("humidityComparedToDayBefore") Double humidityComparedToDayBefore,
      @Param("temperatureCurrent") Double temperatureCurrent,
      @Param("temperatureComparedToDayBefore") Double temperatureComparedToDayBefore,
      @Param("temperatureMin") Double temperatureMin,
      @Param("temperatureMax") Double temperatureMax,
      @Param("windSpeed") Double windSpeed
  );

  // 그 날짜(WeatherPersister.resolveDailyMinMax가 확정한) 전체 row에 min/max를 통일해서 채운다.
  @Modifying
  @Query("UPDATE Weather w SET w.temperatureMin = :min, w.temperatureMax = :max "
      + "WHERE w.grid = :grid AND w.forecastAt >= :start AND w.forecastAt < :end")
  void updateDailyTemperatureRange(
      @Param("grid") Grid grid,
      @Param("min") double min,
      @Param("max") double max,
      @Param("start") Instant start,
      @Param("end") Instant end
  );

  // 그 날짜 row를 전부 앱으로 끌어와 자바에서 집계하는 대신, DB가 조건부 집계(공식 TMN/TMX가 있으면
  // 그 값, 없으면 temperature_current)까지 끝내서 값 2개만 돌려준다. WeatherPersister.resolveDailyMinMax
  // 전용 - 응답을 만드는 동기 경로에서 쓰이므로 왕복을 최소화하기 위함.
  @Query(value = """
      SELECT
          CASE WHEN COUNT(temperature_min) > 0 THEN MIN(temperature_min) ELSE MIN(temperature_current) END AS resolvedMin,
          CASE WHEN COUNT(temperature_max) > 0 THEN MAX(temperature_max) ELSE MAX(temperature_current) END AS resolvedMax
      FROM weathers
      WHERE grid_id = :gridId AND forecast_at >= :start AND forecast_at < :end
      """, nativeQuery = true)
  DailyTemperatureRangeProjection findDailyTemperatureRange(
      @Param("gridId") UUID gridId,
      @Param("start") Instant start,
      @Param("end") Instant end
  );

  interface DailyTemperatureRangeProjection {
    Double getResolvedMin();

    Double getResolvedMax();
  }

  // 오래된 예보(forecast_at < cutoff)를 지운다. 단, 피드가 들고 있는 weather는 제외한다 -
  // feeds.weather_id가 ON DELETE SET NULL이라 지워도 에러는 안 나지만, 피드가 조용히 날씨를 잃어버리게 된다.
  // 한 번에 batchSize개까지만 지우고, 호출부(WeatherCleanupJobConfig)가 리턴값이 0이 될 때까지 반복 호출하는
  // 것을 전제로 한다 - OFFSET 페이징 없이 매번 "지금 기준 조건에 맞는 다음 N개"를 새로 찾기 때문에, 삭제로
  // 인해 뒤 페이지 행들이 앞으로 밀려서 일부가 스킵되는 문제(OFFSET 기반 페이징 + 삭제 조합의 전형적인 버그)가 없다.
  @Modifying
  @Query(value = """
      DELETE FROM weathers
      WHERE id IN (
          SELECT id FROM weathers
          WHERE forecast_at < :cutoff
            AND id NOT IN (SELECT weather_id FROM feeds WHERE weather_id IS NOT NULL)
          ORDER BY id
          LIMIT :batchSize
      )
      """, nativeQuery = true)
  int deleteBatchOlderThan(@Param("cutoff") Instant cutoff, @Param("batchSize") int batchSize);
}
