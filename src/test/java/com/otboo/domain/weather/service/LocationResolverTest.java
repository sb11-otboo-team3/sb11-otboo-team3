package com.otboo.domain.weather.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.otboo.domain.weather.cache.GridRecencyCache;
import com.otboo.domain.weather.client.KakaoLocationClient;
import com.otboo.domain.weather.dto.KakaoRegion;
import com.otboo.domain.weather.dto.WeatherAPILocation;
import com.otboo.domain.weather.entity.Grid;
import com.otboo.domain.weather.exception.GridRegistrationFailedException;
import com.otboo.domain.weather.exception.KakaoApiException;
import com.otboo.domain.weather.repository.GridRepository;
import com.otboo.domain.weather.util.GridConverter;
import com.otboo.domain.weather.util.WeatherGrid;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class LocationResolverTest {

  @Mock
  private GridRepository gridRepository;

  @Mock
  private KakaoLocationClient kakaoLocationClient;

  @Mock
  private GridRecencyCache gridRecencyCache;

  @Mock
  private GridResolver gridResolver;

  private LocationResolver locationResolver;

  @BeforeEach
  void setUp() {
    locationResolver = new LocationResolver(
        new GridConverter(),
        gridRepository,
        kakaoLocationClient,
        gridRecencyCache,
        gridResolver
    );
  }

  private Grid grid(int x, int y) {
    return Grid.builder().x(x).y(y).build();
  }

  @Test
  @DisplayName("항상 카카오 API를 호출해 정확한 행정구역으로 응답한다")
  void alwaysCallsKakaoAndRespondsWithFreshRegion() {
    // given
    double latitude = 37.5665;
    double longitude = 126.9780;
    KakaoRegion region = new KakaoRegion("서울특별시", "강서구", "마곡동");

    given(kakaoLocationClient.getRegion(latitude, longitude)).willReturn(Mono.just(region));
    given(gridResolver.findOrRegister(any())).willReturn(grid(60, 127));

    // when
    WeatherAPILocation result = locationResolver.resolve(latitude, longitude).block();

    // then
    assertThat(result.latitude()).isEqualTo(latitude);
    assertThat(result.longitude()).isEqualTo(longitude);
    assertThat(result.x()).isEqualTo(60);
    assertThat(result.y()).isEqualTo(127);
    assertThat(result.locationNames()).containsExactly("서울특별시", "강서구", "마곡동");
    verify(kakaoLocationClient).getRegion(latitude, longitude);
  }

  @Test
  @DisplayName("최근에 확인된 격자면 GridResolver를 건드리지 않고 바로 응답한다")
  void skipsGridResolverWhenRecentlyConfirmed() {
    // given
    double latitude = 37.5665;
    double longitude = 126.9780;
    KakaoRegion region = new KakaoRegion("서울특별시", "강서구", "마곡동");

    given(kakaoLocationClient.getRegion(latitude, longitude)).willReturn(Mono.just(region));
    given(gridRecencyCache.isRecentlyConfirmed(any())).willReturn(true);

    // when
    WeatherAPILocation result = locationResolver.resolve(latitude, longitude).block();

    // then
    assertThat(result.locationNames()).containsExactly("서울특별시", "강서구", "마곡동");
    verifyNoInteractions(gridResolver);
    verifyNoInteractions(gridRepository);
  }

  @Test
  @DisplayName("GridResolver가 찾아준 격자의 최근 요청 시각을 갱신하고 확인 캐시에 표시한다")
  void refreshesRecencyAndMarksConfirmedForResolvedGrid() {
    // given
    double latitude = 37.5665;
    double longitude = 126.9780;
    KakaoRegion region = new KakaoRegion("서울특별시", "강서구", "마곡동");
    Grid resolved = Mockito.spy(grid(60, 127));

    given(kakaoLocationClient.getRegion(latitude, longitude)).willReturn(Mono.just(region));
    given(gridResolver.findOrRegister(new WeatherGrid(60, 127))).willReturn(resolved);

    // when
    WeatherAPILocation result = locationResolver.resolve(latitude, longitude).block();

    // then
    assertThat(result.locationNames()).containsExactly("서울특별시", "강서구", "마곡동");
    verify(resolved).refreshRequestedAt();
    verify(gridRepository).save(resolved);
    verify(gridRecencyCache).markConfirmed(any());
  }

  @Test
  @DisplayName("GridResolver가 등록에 끝내 실패하면 그 예외가 그대로 전파된다")
  void propagatesExceptionWhenGridResolverFailsToRegister() {
    // given
    double latitude = 37.5665;
    double longitude = 126.9780;
    KakaoRegion region = new KakaoRegion("서울특별시", "강서구", "마곡동");

    given(kakaoLocationClient.getRegion(latitude, longitude)).willReturn(Mono.just(region));
    given(gridResolver.findOrRegister(any()))
        .willThrow(new GridRegistrationFailedException(60, 127));

    // when & then
    assertThatThrownBy(() -> locationResolver.resolve(latitude, longitude).block())
        .isInstanceOf(GridRegistrationFailedException.class);
  }

  @Test
  @DisplayName("이미 알고 있는 지역명이 주어지면 카카오를 호출하지 않고 그 값을 그대로 쓴다")
  void skipsKakaoWhenKnownRegionIsGiven() {
    // given
    double latitude = 37.5665;
    double longitude = 126.9780;
    KakaoRegion knownRegion = new KakaoRegion("서울특별시", "강서구", "마곡동");

    given(gridResolver.findOrRegister(any())).willReturn(grid(60, 127));

    // when
    WeatherAPILocation result = locationResolver.resolve(latitude, longitude, knownRegion).block();

    // then
    assertThat(result.locationNames()).containsExactly("서울특별시", "강서구", "마곡동");
    verifyNoInteractions(kakaoLocationClient);
  }

  @Test
  @DisplayName("이미 알고 있는 지역명을 쓸 때도 격자 레지스트리 갱신은 그대로 실행된다")
  void stillUpdatesGridRegistryWhenUsingKnownRegion() {
    // given
    double latitude = 37.5665;
    double longitude = 126.9780;
    KakaoRegion knownRegion = new KakaoRegion("서울특별시", "강서구", "마곡동");
    Grid resolved = Mockito.spy(grid(60, 127));

    given(gridResolver.findOrRegister(new WeatherGrid(60, 127))).willReturn(resolved);

    // when
    locationResolver.resolve(latitude, longitude, knownRegion).block();

    // then
    verify(resolved).refreshRequestedAt();
    verify(gridRepository).save(resolved);
    verify(gridRecencyCache).markConfirmed(any());
  }

  @Test
  @DisplayName("카카오 호출이 즉시 실패해도 격자 레지스트리 갱신은 취소되지 않고 끝까지 실행된다")
  void gridRegistryUpdateStillCompletesEvenWhenKakaoFailsImmediately() {
    // given: Mono.zip이었다면 카카오가 즉시 실패할 때 아직 시작 안 한 격자 갱신 작업이
    // 취소돼서 실행 자체가 안 될 수 있었다(재현 확인함). zipDelayError로 바꿔서
    // 카카오 성공/실패와 무관하게 격자 갱신이 항상 끝까지 실행되도록 보장한다.
    double latitude = 37.5665;
    double longitude = 126.9780;

    given(kakaoLocationClient.getRegion(latitude, longitude))
        .willReturn(Mono.error(new KakaoApiException(latitude, longitude, new RuntimeException("카카오 장애"))));
    given(gridResolver.findOrRegister(any())).willReturn(grid(60, 127));

    // when
    // then
    assertThatThrownBy(() -> locationResolver.resolve(latitude, longitude).block())
        .isInstanceOf(KakaoApiException.class);

    // block()이 리턴된 시점엔 이미 격자 갱신도 끝나 있어야 한다 (zipDelayError가 둘 다 기다리므로)
    verify(gridResolver).findOrRegister(any());
    verify(gridRecencyCache).markConfirmed(any());
  }
}
