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

  // 같은 grid+forecastAt(대상 시각)에 발표시각이 다른 row가 여러 개 있을 수 있어(유니크 제약이 forecastedAt까지 포함)
  // 가장 최근 발표(forecastedAt 최신)를 하나 골라 가져온다.
  Optional<Weather> findFirstByGridAndForecastAtOrderByForecastedAtDesc(Grid grid, Instant forecastAt);

  // 유니크 제약(grid_id, forecast_at, forecasted_at) 그대로 - 동시 저장 충돌 시 이긴 쪽 row 조회용
  Optional<Weather> findByGridAndForecastAtAndForecastedAt(Grid grid, Instant forecastAt, Instant forecastedAt);

  // 같은 발표(batch) 내에서 특정 날짜에 속하는 예보들 - 일 최저/최고기온 집계용(캐시 미스 시 폴백)
  // [forecastAtStart, forecastAtEndExclusive) 반열림 구간 - 캐시 쪽 필터링과 경계 포함 여부를 맞추기 위해
  // Between(양끝 포함) 대신 GreaterThanEqual/LessThan 조합을 씀.
  List<Weather> findByGridAndForecastedAtAndForecastAtGreaterThanEqualAndForecastAtLessThan(
      Grid grid, Instant forecastedAt, Instant forecastAtStart, Instant forecastAtEndExclusive);
}