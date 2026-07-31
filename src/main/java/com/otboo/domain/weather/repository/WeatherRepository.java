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
}