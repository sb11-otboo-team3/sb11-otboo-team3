package com.otboo.domain.weather.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.otboo.domain.weather.client.KakaoLocationClient;
import com.otboo.domain.weather.client.KakaoRegion;
import com.otboo.domain.weather.dto.WeatherAPILocation;
import com.otboo.domain.weather.entity.Location;
import com.otboo.domain.weather.repository.LocationRepository;
import com.otboo.domain.weather.util.GridConverter;
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

  private WeatherServiceImpl weatherService;

  @BeforeEach
  void setUp() {
    weatherService = new WeatherServiceImpl(
        new GridConverter(),
        locationRepository,
        kakaoLocationClient
    );
  }

  @Test
  @DisplayName("항상 카카오 API를 호출해 정확한 행정구역으로 응답한다")
  void alwaysCallsKakaoAndRespondsWithFreshRegion() {
    // given
    double latitude = 37.5665;
    double longitude = 126.9780;
    KakaoRegion region = new KakaoRegion("서울특별시", "강서구", "마곡동");

    given(kakaoLocationClient.getRegion(latitude, longitude)).willReturn(region);
    given(locationRepository.findByProvinceAndCityAndDistrict("서울특별시", "강서구", "마곡동"))
        .willReturn(Optional.empty());

    // when
    WeatherAPILocation result = weatherService.getLocation(latitude, longitude);

    // then
    assertThat(result.latitude()).isEqualTo(latitude);
    assertThat(result.longitude()).isEqualTo(longitude);
    assertThat(result.x()).isEqualTo(60);
    assertThat(result.y()).isEqualTo(127);
    assertThat(result.locationNames()).containsExactly("서울특별시", "강서구", "마곡동");
    verify(kakaoLocationClient).getRegion(latitude, longitude);
  }

  @Test
  @DisplayName("이미 저장된 행정구역이면 재사용하고 최근 요청 시각을 갱신한다")
  void reusesExistingLocationWhenAdministrativeRegionAlreadyStored() {
    // given
    double latitude = 37.5665;
    double longitude = 126.9780;
    KakaoRegion region = new KakaoRegion("서울특별시", "강서구", "마곡동");
    Location existing = Mockito.spy(Location.builder()
        .x(60)
        .y(127)
        .province("서울특별시")
        .city("강서구")
        .district("마곡동")
        .build());

    given(kakaoLocationClient.getRegion(latitude, longitude)).willReturn(region);
    given(locationRepository.findByProvinceAndCityAndDistrict("서울특별시", "강서구", "마곡동"))
        .willReturn(Optional.of(existing));

    // when
    WeatherAPILocation result = weatherService.getLocation(latitude, longitude);

    // then
    assertThat(result.locationNames()).containsExactly("서울특별시", "강서구", "마곡동");
    verify(existing).refreshRequestedAt();
    verify(locationRepository).save(existing);
  }

  @Test
  @DisplayName("처음 보는 행정구역이면 새로 저장한다")
  void savesNewLocationWhenAdministrativeRegionNotStored() {
    // given
    double latitude = 37.5665;
    double longitude = 126.9780;
    KakaoRegion region = new KakaoRegion("서울특별시", "강서구", "마곡동");

    given(kakaoLocationClient.getRegion(latitude, longitude)).willReturn(region);
    given(locationRepository.findByProvinceAndCityAndDistrict("서울특별시", "강서구", "마곡동"))
        .willReturn(Optional.empty());

    // when
    weatherService.getLocation(latitude, longitude);

    // then
    ArgumentCaptor<Location> captor = ArgumentCaptor.forClass(Location.class);
    verify(locationRepository).save(captor.capture());
    Location saved = captor.getValue();
    assertThat(saved.getX()).isEqualTo(60);
    assertThat(saved.getY()).isEqualTo(127);
    assertThat(saved.getProvince()).isEqualTo("서울특별시");
    assertThat(saved.getCity()).isEqualTo("강서구");
    assertThat(saved.getDistrict()).isEqualTo("마곡동");
  }

  @Test
  @DisplayName("동시에 같은 행정구역이 먼저 저장돼 유니크 제약 위반이 나도, 이미 확보한 카카오 응답으로 정상 반환한다")
  void returnsFreshRegionEvenWhenConcurrentSaveConflicts() {
    // given
    double latitude = 37.5665;
    double longitude = 126.9780;
    KakaoRegion region = new KakaoRegion("서울특별시", "강서구", "마곡동");

    given(kakaoLocationClient.getRegion(latitude, longitude)).willReturn(region);
    given(locationRepository.findByProvinceAndCityAndDistrict("서울특별시", "강서구", "마곡동"))
        .willReturn(Optional.empty());
    given(locationRepository.save(any(Location.class)))
        .willThrow(new DataIntegrityViolationException("duplicate key"));

    // when
    WeatherAPILocation result = weatherService.getLocation(latitude, longitude);

    // then
    assertThat(result.locationNames()).containsExactly("서울특별시", "강서구", "마곡동");
  }
}
