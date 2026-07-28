package com.otboo.domain.weather.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import com.otboo.domain.weather.cache.LocationRegionCache;
import com.otboo.domain.weather.client.KakaoLocationClient;
import com.otboo.domain.weather.client.KakaoRegion;
import com.otboo.domain.weather.dto.WeatherAPILocation;
import com.otboo.domain.weather.repository.LocationRepository;
import com.otboo.domain.weather.util.GridConverter;
import com.otboo.domain.weather.util.WeatherGrid;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WeatherServiceImplTest {

  @Mock
  private LocationRepository locationRepository;

  @Mock
  private KakaoLocationClient kakaoLocationClient;

  @Mock
  private LocationRegionCache locationRegionCache;

  private WeatherServiceImpl weatherService;

  @BeforeEach
  void setUp() {
    weatherService = new WeatherServiceImpl(
        new GridConverter(),
        locationRepository,
        kakaoLocationClient,
        locationRegionCache
    );
  }

  @Test
  @DisplayName("캐시에 값이 있으면 DB와 카카오 API를 호출하지 않고 캐시 값으로 응답한다")
  void returnsFromCacheWhenCacheHit() {
    // given
    double latitude = 37.5665;
    double longitude = 126.9780;
    WeatherGrid grid = new WeatherGrid(60, 127);
    KakaoRegion cachedRegion = new KakaoRegion("서울특별시", "강서구", "마곡동");
    given(locationRegionCache.get(grid)).willReturn(Optional.of(cachedRegion));

    // when
    WeatherAPILocation result = weatherService.getLocation(latitude, longitude);

    // then
    assertThat(result.latitude()).isEqualTo(latitude);
    assertThat(result.longitude()).isEqualTo(longitude);
    assertThat(result.x()).isEqualTo(60);
    assertThat(result.y()).isEqualTo(127);
    assertThat(result.locationNames()).containsExactly("서울특별시", "강서구", "마곡동");
    verifyNoInteractions(locationRepository, kakaoLocationClient);
  }
}
