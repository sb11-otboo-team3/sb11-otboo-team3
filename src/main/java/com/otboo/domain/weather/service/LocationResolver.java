package com.otboo.domain.weather.service;

import com.otboo.domain.weather.cache.GridRecencyCache;
import com.otboo.domain.weather.client.KakaoLocationClient;
import com.otboo.domain.weather.dto.KakaoRegion;
import com.otboo.domain.weather.dto.WeatherAPILocation;
import com.otboo.domain.weather.entity.Grid;
import com.otboo.domain.weather.repository.GridRepository;
import com.otboo.domain.weather.util.GridConverter;
import com.otboo.domain.weather.util.WeatherGrid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Slf4j
@Component
@RequiredArgsConstructor
public class LocationResolver {

  private final GridConverter gridConverter;
  private final GridRepository gridRepository;
  private final KakaoLocationClient kakaoLocationClient;
  private final GridRecencyCache gridRecencyCache;
  private final GridResolver gridResolver;

  public Mono<WeatherAPILocation> resolve(double latitude, double longitude) {
    return resolve(latitude, longitude, null);
  }

  // knownRegion: 호출부가 이미 검증된 지역명을 갖고 있으면 카카오 호출을 생략하고 그대로 쓴다.
  // null이면 지금까지와 동일하게 매번 카카오를 호출한다. 격자 레지스트리 갱신은 두 경우 모두 동일하게 실행된다.
  public Mono<WeatherAPILocation> resolve(double latitude, double longitude, KakaoRegion knownRegion) {
    WeatherGrid grid = gridConverter.convert(latitude, longitude);

    // 카카오 호출과 격자 레지스트리 갱신(DB/캐시)은 서로 결과를 필요로 하지 않는 독립적인 작업이라 동시에 실행한다.
    Mono<KakaoRegion> regionMono = knownRegion != null
        ? Mono.just(knownRegion)
        : kakaoLocationClient.getRegion(latitude, longitude);

    // 격자 레지스트리 갱신은 JPA(블로킹) 호출이라, 이벤트 루프 스레드가 아니라 별도 스레드풀(boundedElastic)에서 실행한다.
    Mono<Boolean> gridRegistryMono = Mono.fromCallable(() -> {
          updateGridRegistry(grid);
          return true;
        })
        .subscribeOn(Schedulers.boundedElastic());

    // Mono.zip은 한쪽이 실패하면 아직 시작도 안 한 다른 쪽을 즉시 취소해버린다 - 그러면 카카오가
    // 빨리 실패할 때 격자 레지스트리 갱신 자체가 통째로 스킵될 수 있다(타이밍에 따라 달라지는 경쟁 상태).
    // zipDelayError는 둘 다 끝날 때까지 기다렸다가 에러를 전파해서, 격자 갱신은 카카오 성공/실패와
    // 무관하게 항상 끝까지 실행되는 걸 보장한다.
    return Mono.zipDelayError(regionMono, gridRegistryMono)
        .map(tuple -> toDto(latitude, longitude, grid, tuple.getT1()));
  }

  //최근 접근 한적 있는 격자인지 캐시에서 체크.(너무 많은 최근 접근한 지역인지 DB 접근을 줄이기 위해)
  private void updateGridRegistry(WeatherGrid grid) {
    if (gridRecencyCache.isRecentlyConfirmed(grid)) {
      return;
    }

    // 격자 레지스트리 갱신 (날씨 프리페치 배치가 실제로 쓰이는 격자만 골라낼 때 참고할 용도)
    // 찾고 없으면 등록하는 로직 자체는 GridResolver로 통일 - 여기선 그 결과에 최근 요청 시각만 갱신.
    Grid found = gridResolver.findOrRegister(grid);
    found.refreshRequestedAt();
    gridRepository.save(found);

    gridRecencyCache.markConfirmed(grid);
  }

  private WeatherAPILocation toDto(
      double latitude, double longitude, WeatherGrid grid, KakaoRegion region
  ) {
    return new WeatherAPILocation(
        latitude,
        longitude,
        grid.x(),
        grid.y(),
        List.of(region.province(), region.city(), region.district())
    );
  }
}
