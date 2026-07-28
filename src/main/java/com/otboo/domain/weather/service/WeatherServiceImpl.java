package com.otboo.domain.weather.service;

import com.otboo.domain.weather.cache.LocationRegionCache;
import com.otboo.domain.weather.client.KakaoLocationClient;
import com.otboo.domain.weather.client.KakaoRegion;
import com.otboo.domain.weather.dto.WeatherAPILocation;
import com.otboo.domain.weather.repository.LocationRepository;
import com.otboo.domain.weather.util.GridConverter;
import com.otboo.domain.weather.util.WeatherGrid;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class WeatherServiceImpl implements WeatherService {

  private final GridConverter gridConverter;
  private final LocationRepository locationRepository;
  private final KakaoLocationClient kakaoLocationClient;
  private final LocationRegionCache locationRegionCache;

  public WeatherServiceImpl(
      GridConverter gridConverter,
      LocationRepository locationRepository,
      KakaoLocationClient kakaoLocationClient,
      LocationRegionCache locationRegionCache
  ) {
    this.gridConverter = gridConverter;
    this.locationRepository = locationRepository;
    this.kakaoLocationClient = kakaoLocationClient;
    this.locationRegionCache = locationRegionCache;
  }

  @Override
  public WeatherAPILocation getLocation(double latitude, double longitude) {
    WeatherGrid grid = gridConverter.convert(latitude, longitude);

    Optional<KakaoRegion> cached = locationRegionCache.get(grid);
    if (cached.isPresent()) {
      return toDto(latitude, longitude, grid, cached.get());
    }

    throw new UnsupportedOperationException("아직 구현되지 않았습니다.");
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