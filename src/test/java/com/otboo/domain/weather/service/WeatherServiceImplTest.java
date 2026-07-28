package com.otboo.domain.weather.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.otboo.domain.weather.cache.LocationRegionCache;
import com.otboo.domain.weather.client.KakaoLocationClient;
import com.otboo.domain.weather.client.KakaoRegion;
import com.otboo.domain.weather.dto.WeatherAPILocation;
import com.otboo.domain.weather.entity.Location;
import com.otboo.domain.weather.repository.LocationRepository;
import com.otboo.domain.weather.util.GridConverter;
import com.otboo.domain.weather.util.WeatherGrid;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

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

  @Test
  @DisplayName("캐시엔 없지만 DB에 저장된 좌표면 최근 요청 시각을 갱신하고 캐시를 채운 뒤 반환한다")
  void returnsFromDbAndRefreshesWhenDbHit() {
    // given
    double latitude = 37.5665;
    double longitude = 126.9780;
    WeatherGrid grid = new WeatherGrid(60, 127);
    Location existing = Mockito.spy(Location.builder()
        .x(60)
        .y(127)
        .province("서울특별시")
        .city("강서구")
        .district("마곡동")
        .build());

    given(locationRegionCache.get(grid)).willReturn(Optional.empty());
    given(locationRepository.findByXAndY(60, 127)).willReturn(Optional.of(existing));

    // when
    WeatherAPILocation result = weatherService.getLocation(latitude, longitude);

    // then
    assertThat(result.latitude()).isEqualTo(latitude);
    assertThat(result.longitude()).isEqualTo(longitude);
    assertThat(result.x()).isEqualTo(60);
    assertThat(result.y()).isEqualTo(127);
    assertThat(result.locationNames()).containsExactly("서울특별시", "강서구", "마곡동");
    verify(existing).refreshRequestedAt();
    verify(locationRepository).save(existing);
    verify(locationRegionCache).put(grid, new KakaoRegion("서울특별시", "강서구", "마곡동"));
    verifyNoInteractions(kakaoLocationClient);
  }

  @Test
  @DisplayName("캐시와 DB에 모두 없으면 카카오 API를 호출해 새로 저장하고 캐시를 채운 뒤 반환한다")
  void callsKakaoAndSavesWhenFullMiss() {
    // given
    double latitude = 37.5665;
    double longitude = 126.9780;
    WeatherGrid grid = new WeatherGrid(60, 127);
    KakaoRegion region = new KakaoRegion("서울특별시", "강서구", "마곡동");

    given(locationRegionCache.get(grid)).willReturn(Optional.empty());
    given(locationRepository.findByXAndY(60, 127)).willReturn(Optional.empty());
    given(kakaoLocationClient.getRegion(latitude, longitude)).willReturn(region);

    // when
    WeatherAPILocation result = weatherService.getLocation(latitude, longitude);

    // then
    assertThat(result.latitude()).isEqualTo(latitude);
    assertThat(result.longitude()).isEqualTo(longitude);
    assertThat(result.x()).isEqualTo(60);
    assertThat(result.y()).isEqualTo(127);
    assertThat(result.locationNames()).containsExactly("서울특별시", "강서구", "마곡동");

    ArgumentCaptor<Location> captor = ArgumentCaptor.forClass(Location.class);
    verify(locationRepository).save(captor.capture());
    Location saved = captor.getValue();
    assertThat(saved.getX()).isEqualTo(60);
    assertThat(saved.getY()).isEqualTo(127);
    assertThat(saved.getProvince()).isEqualTo("서울특별시");
    assertThat(saved.getCity()).isEqualTo("강서구");
    assertThat(saved.getDistrict()).isEqualTo("마곡동");

    verify(locationRegionCache).put(grid, region);
  }

  @Test
  @DisplayName("동시에 같은 좌표가 저장되어 유니크 제약 위반이 발생하면, 이미 저장된 값을 다시 조회해서 반환한다")
  void returnsWinnerRowWhenConcurrentSaveConflicts() {
    // given
    double latitude = 37.5665;
    double longitude = 126.9780;
    WeatherGrid grid = new WeatherGrid(60, 127);
    KakaoRegion regionFromKakao = new KakaoRegion("서울특별시", "강서구", "마곡동");
    Location winnerRow = Location.builder()
        .x(60)
        .y(127)
        .province("서울특별시")
        .city("강서구")
        .district("마곡동")
        .build();

    given(locationRegionCache.get(grid)).willReturn(Optional.empty());
    given(locationRepository.findByXAndY(60, 127))
        .willReturn(Optional.empty(), Optional.of(winnerRow));
    given(kakaoLocationClient.getRegion(latitude, longitude)).willReturn(regionFromKakao);
    given(locationRepository.save(any(Location.class)))
        .willThrow(new DataIntegrityViolationException("duplicate key"));

    // when
    WeatherAPILocation result = weatherService.getLocation(latitude, longitude);

    // then
    assertThat(result.locationNames()).containsExactly("서울특별시", "강서구", "마곡동");
    verify(locationRepository, Mockito.times(2)).findByXAndY(60, 127);
    verify(locationRegionCache).put(grid, regionFromKakao);
  }
}