package com.otboo.domain.weather.service;

import com.otboo.domain.weather.cache.LocationRegionCache;
import com.otboo.domain.weather.client.KakaoLocationClient;
import com.otboo.domain.weather.client.KakaoRegion;
import com.otboo.domain.weather.dto.WeatherAPILocation;
import com.otboo.domain.weather.entity.Location;
import com.otboo.domain.weather.repository.LocationRepository;
import com.otboo.domain.weather.util.GridConverter;
import com.otboo.domain.weather.util.WeatherGrid;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WeatherServiceImpl implements WeatherService {

  private final GridConverter gridConverter;
  private final LocationRepository locationRepository;
  private final KakaoLocationClient kakaoLocationClient;
  private final LocationRegionCache locationRegionCache;



  @Override
  public WeatherAPILocation getLocation(double latitude, double longitude) {
    WeatherGrid grid = gridConverter.convert(latitude, longitude);

    // 캐시 히트시 반환
    Optional<KakaoRegion> cached = locationRegionCache.get(grid);
    if (cached.isPresent()) {
      return toDto(latitude, longitude, grid, cached.get());
    }

    // DB 히트시 반환
    Optional<Location> existing = locationRepository.findByXAndY(grid.x(), grid.y());
    if (existing.isPresent()) {
      Location location = existing.get();
      location.refreshRequestedAt(); // 최근 사용 시간 기록.
      locationRepository.save(location);

      KakaoRegion region = new KakaoRegion(
          location.getProvince(), location.getCity(), location.getDistrict());
      locationRegionCache.put(grid, region);
      return toDto(latitude, longitude, grid, region);
    }

    // 모두 미스시 카카오 api 사용
    KakaoRegion region = kakaoLocationClient.getRegion(latitude, longitude);
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
      // 동시에 같은 좌표가 먼저 저장된 경우, 그 값을 그대로 사용 (동시성 제어)
      Location winner = locationRepository.findByXAndY(grid.x(), grid.y())
          .orElseThrow(() -> e);
      region = new KakaoRegion(winner.getProvince(), winner.getCity(), winner.getDistrict());
    }

    locationRegionCache.put(grid, region);

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