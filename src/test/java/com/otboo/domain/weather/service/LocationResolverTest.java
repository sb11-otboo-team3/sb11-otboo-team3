package com.otboo.domain.weather.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.otboo.domain.weather.cache.GridRecencyCache;
import com.otboo.domain.weather.client.KakaoLocationClient;
import com.otboo.domain.weather.dto.KakaoRegion;
import com.otboo.domain.weather.dto.WeatherAPILocation;
import com.otboo.domain.weather.entity.Grid;
import com.otboo.domain.weather.repository.GridRepository;
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
class LocationResolverTest {

  @Mock
  private GridRepository gridRepository;

  @Mock
  private KakaoLocationClient kakaoLocationClient;

  @Mock
  private GridRecencyCache gridRecencyCache;

  @Mock
  private GridSaver gridSaver;

  private LocationResolver locationResolver;

  @BeforeEach
  void setUp() {
    locationResolver = new LocationResolver(
        new GridConverter(),
        gridRepository,
        kakaoLocationClient,
        gridRecencyCache,
        gridSaver
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
    given(gridRepository.findByXAndY(60, 127)).willReturn(Optional.empty());

    // when
    WeatherAPILocation result = locationResolver.resolve(latitude, longitude);

    // then
    assertThat(result.latitude()).isEqualTo(latitude);
    assertThat(result.longitude()).isEqualTo(longitude);
    assertThat(result.x()).isEqualTo(60);
    assertThat(result.y()).isEqualTo(127);
    assertThat(result.locationNames()).containsExactly("서울특별시", "강서구", "마곡동");
    verify(kakaoLocationClient).getRegion(latitude, longitude);
  }

  @Test
  @DisplayName("최근에 확인된 격자면 DB를 건드리지 않고 바로 응답한다")
  void skipsDbWhenRecentlyConfirmed() {
    // given
    double latitude = 37.5665;
    double longitude = 126.9780;
    KakaoRegion region = new KakaoRegion("서울특별시", "강서구", "마곡동");

    given(kakaoLocationClient.getRegion(latitude, longitude)).willReturn(region);
    given(gridRecencyCache.isRecentlyConfirmed(any())).willReturn(true);

    // when
    WeatherAPILocation result = locationResolver.resolve(latitude, longitude);

    // then
    assertThat(result.locationNames()).containsExactly("서울특별시", "강서구", "마곡동");
    verifyNoInteractions(gridRepository);
    verifyNoInteractions(gridSaver);
  }

  @Test
  @DisplayName("이미 등록된 격자면 새로 저장하지 않고 최근 요청 시각만 갱신한다")
  void refreshesRecencyWhenGridAlreadyRegistered() {
    // given
    double latitude = 37.5665;
    double longitude = 126.9780;
    KakaoRegion region = new KakaoRegion("서울특별시", "강서구", "마곡동");
    Grid existing = Mockito.spy(Grid.builder().x(60).y(127).build());

    given(kakaoLocationClient.getRegion(latitude, longitude)).willReturn(region);
    given(gridRepository.findByXAndY(60, 127)).willReturn(Optional.of(existing));

    // when
    WeatherAPILocation result = locationResolver.resolve(latitude, longitude);

    // then
    assertThat(result.locationNames()).containsExactly("서울특별시", "강서구", "마곡동");
    verify(existing).refreshRequestedAt();
    verify(gridRepository).save(existing);
    verify(gridSaver, never()).saveInNewTransaction(any(Grid.class));
    verify(gridRecencyCache).markConfirmed(any());
  }

  @Test
  @DisplayName("처음 보는 격자면 레지스트리에 등록한다")
  void registersNewGridWhenNotYetRegistered() {
    // given
    double latitude = 37.5665;
    double longitude = 126.9780;
    KakaoRegion region = new KakaoRegion("서울특별시", "강서구", "마곡동");

    given(kakaoLocationClient.getRegion(latitude, longitude)).willReturn(region);
    given(gridRepository.findByXAndY(60, 127)).willReturn(Optional.empty());

    // when
    locationResolver.resolve(latitude, longitude);

    // then
    ArgumentCaptor<Grid> captor = ArgumentCaptor.forClass(Grid.class);
    verify(gridSaver).saveInNewTransaction(captor.capture());
    Grid saved = captor.getValue();
    assertThat(saved.getX()).isEqualTo(60);
    assertThat(saved.getY()).isEqualTo(127);
    verify(gridRecencyCache).markConfirmed(any());
  }

  @Test
  @DisplayName("동시에 같은 격자가 먼저 등록돼 유니크 제약 위반이 나도, 이미 확보한 카카오 응답으로 정상 반환한다")
  void returnsFreshRegionEvenWhenConcurrentSaveConflicts() {
    // given
    double latitude = 37.5665;
    double longitude = 126.9780;
    KakaoRegion region = new KakaoRegion("서울특별시", "강서구", "마곡동");

    given(kakaoLocationClient.getRegion(latitude, longitude)).willReturn(region);
    given(gridRepository.findByXAndY(60, 127)).willReturn(Optional.empty());
    Mockito.doThrow(new DataIntegrityViolationException("duplicate key"))
        .when(gridSaver).saveInNewTransaction(any(Grid.class));

    // when
    WeatherAPILocation result = locationResolver.resolve(latitude, longitude);

    // then
    assertThat(result.locationNames()).containsExactly("서울특별시", "강서구", "마곡동");
    verify(gridRecencyCache).markConfirmed(any());
  }
}
