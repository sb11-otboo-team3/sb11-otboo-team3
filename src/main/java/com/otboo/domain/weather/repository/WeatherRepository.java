package com.otboo.domain.weather.repository;

import com.otboo.domain.weather.entity.Grid;
import com.otboo.domain.weather.entity.Weather;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WeatherRepository extends JpaRepository<Weather, UUID> {

  List<Weather> findByGridAndForecastedAt(Grid grid, Instant forecastedAt);

  Optional<Weather> findByGridAndForecastAt(Grid grid, Instant forecastAt);

  // 유니크 제약(grid_id, forecast_at, forecasted_at) 그대로 - 동시 저장 충돌 시 이긴 쪽 row 조회용
  Optional<Weather> findByGridAndForecastAtAndForecastedAt(Grid grid, Instant forecastAt, Instant forecastedAt);

  // 같은 발표(batch) 내에서 특정 날짜에 속하는 예보들 - 일 최저/최고기온 집계용(캐시 미스 시 폴백)
  List<Weather> findByGridAndForecastedAtAndForecastAtBetween(
      Grid grid, Instant forecastedAt, Instant forecastAtStart, Instant forecastAtEnd);
}