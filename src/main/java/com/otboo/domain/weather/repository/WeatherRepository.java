package com.otboo.domain.weather.repository;

import com.otboo.domain.weather.entity.Grid;
import com.otboo.domain.weather.entity.Weather;
import java.time.Instant;
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

  // 발표(forecastedAt)와 무관하게, 같은 grid+날짜에 걸리는 행 전부 - 일 최저/최고기온 집계(reconcileDailyMinMax)와
  // WeatherSummaryFinder의 캐시 미스 폴백에서 공용으로 씀. upsert 구조라 시간대별로 row가 하나씩만 있어서
  // 중복 없이 그 날짜의 "현재 알려진 가장 최신" 예보 전체를 그대로 가져오는 것과 같다.
  // [forecastAtStart, forecastAtEndExclusive) 반열림 구간.
  List<Weather> findByGridAndForecastAtGreaterThanEqualAndForecastAtLessThan(
      Grid grid, Instant forecastAtStart, Instant forecastAtEndExclusive);

  // 같은 (grid_id, forecast_at)에 이미 row가 있으면 최신 값으로 덮어쓰고(id/created_at은 유지),
  // 없으면 새로 만든다. RETURNING으로 결과 row를 한 번의 왕복으로 그대로 받아온다.
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
      RETURNING *
      """, nativeQuery = true)
  Weather upsert(
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

  // 그 날짜(reconcileDailyMinMax가 확정한) 전체 row에 min/max를 통일해서 채운다.
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
}
