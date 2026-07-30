package com.otboo.domain.weather.service;

import com.otboo.domain.weather.cache.LocationRecencyCache;
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
  private final LocationRecencyCache locationRecencyCache;

  @Override
  @Transactional
  public WeatherAPILocation getLocation(double latitude, double longitude) {
    WeatherGrid grid = gridConverter.convert(latitude, longitude);

    // 카카오에서 조회
    KakaoRegion region = kakaoLocationClient.getRegion(latitude, longitude);

    if (locationRecencyCache.isRecentlyConfirmed(region)) {
      // 캐시가 있으며 저장하지 않음.(자주 최근 접근 시간을 갱신하지 않게)
      return toDto(latitude, longitude, grid, region);
    }

    // DB에서 가져오기
    Optional<Location> existing = locationRepository.findByProvinceAndCityAndDistrict(
        region.province(), region.city(), region.district());

    //DB에 있으면 가져오기.
    if (existing.isPresent()) {
      log.info("행정구역 찾기 - 기존 행정구역 재사용, province={}, city={}, district={}",
          region.province(), region.city(), region.district());

      Location location = existing.get();
      location.refreshRequestedAt(); // 최근 사용 시간 기록 갱신.
      locationRepository.save(location);
    } else {

      //없으면 값 새로 저장
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
        // 동시성 문제 발생시,
        log.warn("행정구역 찾기 - 동시성 충돌 발생, province={}, city={}, district={}",
            region.province(), region.city(), region.district(), e);
      }
    }

    locationRecencyCache.markConfirmed(region);

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