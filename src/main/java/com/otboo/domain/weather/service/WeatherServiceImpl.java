package com.otboo.domain.weather.service;

import com.otboo.domain.weather.client.KakaoLocationClient;
import com.otboo.domain.weather.client.KakaoRegion;
import com.otboo.domain.weather.dto.WeatherAPILocation;
import com.otboo.domain.weather.entity.Location;
import com.otboo.domain.weather.repository.LocationRepository;
import com.otboo.domain.weather.util.GridConverter;
import com.otboo.domain.weather.util.WeatherGrid;
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

  private final GridConverter gridConverter;
  private final LocationRepository locationRepository;
  private final KakaoLocationClient kakaoLocationClient;

  @Override
  @Transactional
  public WeatherAPILocation getLocation(double latitude, double longitude) {
    WeatherGrid grid = gridConverter.convert(latitude, longitude);

    // 행정구역은 항상 카카오에서 정확하게 조회 (격자 단위로 캐싱하면 동 경계에서 부정확해짐)
    KakaoRegion region = kakaoLocationClient.getRegion(latitude, longitude);

    Optional<Location> existing = locationRepository.findByProvinceAndCityAndDistrict(
        region.province(), region.city(), region.district());

    if (existing.isPresent()) {
      log.info("행정구역 찾기 - 기존 행정구역 재사용, province={}, city={}, district={}",
          region.province(), region.city(), region.district());

      Location location = existing.get();
      location.refreshRequestedAt(); // 최근 사용 시간 기록.
      locationRepository.save(location);
    } else {
      log.info("행정구역 찾기 - 신규 행정구역 저장, province={}, city={}, district={}",
          region.province(), region.city(), region.district());

      Location location = Location.builder()
          .x(grid.x())
          .y(grid.y())
          .province(region.province())
          .city(region.city())
          .district(region.district())
          .build();

      try {
        locationRepository.save(location);
      } catch (DataIntegrityViolationException e) {
        // 동시에 같은 행정구역이 먼저 저장된 경우. 응답은 이미 카카오 조회 결과(region)로
        // 확정되어 있으므로 별도 조회 없이 무시한다.
        log.warn("행정구역 찾기 - 동시성 충돌 발생, province={}, city={}, district={}",
            region.province(), region.city(), region.district(), e);
      }
    }

    return toDto(latitude, longitude, grid, region);
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